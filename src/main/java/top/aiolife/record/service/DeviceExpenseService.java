package top.aiolife.record.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import top.aiolife.record.convertor.RecordApiConvertor;
import top.aiolife.sso.service.SecondaryLockGuard;
import top.aiolife.core.exception.SecondaryLockRequiredException;
import top.aiolife.record.mapper.DeviceExpenseMapper;
import top.aiolife.record.mapper.IDeviceMapper;
import top.aiolife.record.mapper.IExpenseMapper;
import top.aiolife.record.pojo.entity.DeviceExpenseEntity;
import top.aiolife.record.pojo.entity.DeviceEntity;
import top.aiolife.record.pojo.entity.ExpenseEntity;
import top.aiolife.record.pojo.req.DeviceCreateReq;
import top.aiolife.record.pojo.vo.DeviceVO;
import top.aiolife.record.pojo.vo.ExpenseDeviceContextVO;
import top.aiolife.record.pojo.vo.ExpenseDeviceVO;
import top.aiolife.record.pojo.vo.ExpenseVO;

@Service
@RequiredArgsConstructor
public class DeviceExpenseService {
    private final DeviceExpenseMapper links;
    private final IExpenseMapper expenses;
    private final IDeviceMapper devices;
    private final SecondaryLockGuard locks;

    public ExpenseDeviceContextVO context(long userId, long expenseId) {
        var expense = expenses.selectOne(new LambdaQueryWrapper<ExpenseEntity>()
                .eq(ExpenseEntity::getUserId, userId).eq(ExpenseEntity::getId, expenseId));
        if (expense == null) throw missing("支出");
        var defaults = new DeviceCreateReq();
        defaults.setName(shortText(expense.getExpDesc()));
        if (defaults.getName() == null) defaults.setName(shortText(expense.getRemark()));
        defaults.setPurchasePrice(expense.getAmt());
        defaults.setPurchaseDate(expense.getExpTime() == null ? null : expense.getExpTime().toLocalDate());
        defaults.setPurchasePlace(shortText(expense.getCounterparty()));
        defaults.setRemark(shortText(expense.getRemark()));
        defaults.setStatus("1");
        var found = links.findLinks(userId, List.of(expenseId));
        DeviceVO device = found.isEmpty() ? null : RecordApiConvertor.INSTANCE.toDeviceVO(
                devices.selectOne(new LambdaQueryWrapper<DeviceEntity>()
                        .eq(DeviceEntity::getId, found.getFirst().getDeviceId()).eq(DeviceEntity::getUserId, userId)));
        return new ExpenseDeviceContextVO(device, defaults);
    }

    public void enrich(long userId, List<ExpenseVO> rows) {
        if (rows == null || rows.isEmpty()) return;
        // 设备墙锁定时，普通账单仍可读取，但不泄露关联设备名称。
        try { locks.checkMenus(userId, "/my-hub/device"); }
        catch (SecondaryLockRequiredException exception) { return; }
        var byId = links.findLinks(userId, rows.stream().map(ExpenseVO::getId).toList()).stream()
                .collect(Collectors.toMap(ExpenseDeviceVO::getExpenseId, Function.identity()));
        rows.forEach(row -> {
            var link = byId.get(row.getId());
            if (link != null) { row.setDeviceId(link.getDeviceId()); row.setDeviceName(link.getDeviceName()); }
        });
    }

    @Transactional(rollbackFor = Exception.class)
    public DeviceVO bind(long userId, long expenseId, long deviceId) {
        requireExpenseLock(userId, expenseId);
        var device = devices.lockOwned(userId, deviceId);
        if (device == null) throw missing("设备");
        saveLink(userId, expenseId, deviceId);
        return RecordApiConvertor.INSTANCE.toDeviceVO(device);
    }

    @Transactional(rollbackFor = Exception.class)
    public DeviceVO createAndBind(long userId, long expenseId, DeviceCreateReq request) {
        requireExpenseLock(userId, expenseId);
        // 已绑定时拒绝新建，避免重复提交创建多个设备；更换已有设备使用 bind。
        if (!links.findLinks(userId, List.of(expenseId)).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该支出已绑定设备，请重新打开查看");
        }
        if (request.getName() == null || request.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写设备名称");
        }
        if (request.getEndDate() != null && request.getPurchaseDate() != null
                && request.getEndDate().isBefore(request.getPurchaseDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "结束日期不能早于购买日期");
        }
        var device = RecordApiConvertor.INSTANCE.fromDeviceCreateReq(request);
        device.setName(request.getName().trim());
        device.setUserId(userId);
        device.fillCreateCommonField(userId);
        devices.insert(device);
        saveLink(userId, expenseId, device.getId());
        return RecordApiConvertor.INSTANCE.toDeviceVO(device);
    }

    @Transactional(rollbackFor = Exception.class)
    public void unbind(long userId, long expenseId) {
        requireExpenseLock(userId, expenseId);
        clearLinks(userId, List.of(expenseId), null);
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean deleteExpenses(long userId, List<Long> ids) {
        // 与绑定统一按支出 -> 设备/关联的顺序加锁；批量按 ID 排序。
        var owned = ids.stream().distinct().sorted()
                .filter(id -> expenses.lockOwned(userId, id) != null).toList();
        if (owned.isEmpty()) return false;
        clearLinks(userId, owned, null);
        return expenses.delete(new LambdaQueryWrapper<ExpenseEntity>()
                .eq(ExpenseEntity::getUserId, userId).in(ExpenseEntity::getId, owned)) > 0;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean deleteDevice(long userId, long deviceId) {
        if (devices.lockOwned(userId, deviceId) == null) return false;
        clearLinks(userId, null, deviceId);
        return devices.delete(new LambdaQueryWrapper<DeviceEntity>()
                .eq(DeviceEntity::getUserId, userId).eq(DeviceEntity::getId, deviceId)) > 0;
    }

    private void requireExpenseLock(long userId, long expenseId) {
        if (expenses.lockOwned(userId, expenseId) == null) throw missing("支出");
    }

    private void saveLink(long userId, long expenseId, long deviceId) {
        var link = new DeviceExpenseEntity();
        link.setId(IdWorker.getId());
        link.setUserId(userId);
        link.setDeviceId(deviceId);
        link.setExpenseId(expenseId);
        links.bind(link);
    }

    private void clearLinks(long userId, List<Long> expenseIds, Long deviceId) {
        var update = new LambdaUpdateWrapper<DeviceExpenseEntity>()
                .eq(DeviceExpenseEntity::getUserId, userId)
                .eq(deviceId != null, DeviceExpenseEntity::getDeviceId, deviceId)
                .set(DeviceExpenseEntity::getIsDeleted, 1)
                .set(DeviceExpenseEntity::getUpdateUser, userId)
                .set(DeviceExpenseEntity::getUpdateTime, LocalDateTime.now());
        if (expenseIds != null) update.in(DeviceExpenseEntity::getExpenseId, expenseIds);
        links.update(null, update);
    }

    private String shortText(String text) {
        if (text == null || text.isBlank() || "/".equals(text.trim())) return null;
        String trimmed = text.trim();
        return trimmed.substring(0, Math.min(trimmed.length(), 255));
    }

    private ResponseStatusException missing(String kind) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, kind + "不存在或已删除");
    }
}
