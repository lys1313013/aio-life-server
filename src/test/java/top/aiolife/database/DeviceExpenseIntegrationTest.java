package top.aiolife.database;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import top.aiolife.core.exception.SecondaryLockRequiredException;
import top.aiolife.record.convertor.RecordApiConvertor;
import top.aiolife.record.mapper.DeviceExpenseMapper;
import top.aiolife.record.mapper.IDeviceMapper;
import top.aiolife.record.mapper.IExpenseMapper;
import top.aiolife.record.pojo.entity.DeviceEntity;
import top.aiolife.record.pojo.entity.DeviceExpenseEntity;
import top.aiolife.record.pojo.entity.ExpenseEntity;
import top.aiolife.record.pojo.req.DeviceCreateReq;
import top.aiolife.record.pojo.req.ExpenseCreateReq;
import top.aiolife.record.service.DeviceExpenseService;
import top.aiolife.record.service.impl.ExpenseServiceImpl;
import top.aiolife.sso.service.SecondaryLockGuard;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.any;

/** 隔离 PostgreSQL 库中的关联行为，所有夹具由事务回滚。 */
@ActiveProfiles("test")
@SpringBootTest(classes = PhysicalTableEntitySelectIntegrationTest.DatabaseMapperTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import({DeviceExpenseService.class, ExpenseServiceImpl.class})
@Transactional
class DeviceExpenseIntegrationTest {
    @Autowired private DeviceExpenseService service;
    @Autowired private IDeviceMapper devices;
    @Autowired private IExpenseMapper expenses;
    @MockitoSpyBean private DeviceExpenseMapper links;
    @Autowired private PlatformTransactionManager transactions;
    @MockitoBean private SecondaryLockGuard locks;

    @Test void 新增支出同时关联已有设备或新建设备并保留普通录入() {
        var device = device(1L);
        var existing = draftExpense(); existing.setDeviceId(device.getId());
        var fresh = draftExpense();
        var newDevice = new DeviceCreateReq(); newDevice.setName("随支出新建的设备");
        newDevice.setPurchasePrice(new BigDecimal("42.50"));
        fresh.setNewDevice(newDevice);
        assertTrue(service.createExpenses(1L, List.of(existing, fresh, draftExpense())));
        var rows = expenses.selectList(new LambdaQueryWrapper<ExpenseEntity>()
                .eq(ExpenseEntity::getUserId, 1L).eq(ExpenseEntity::getRemark, "新增关联测试").orderByAsc(ExpenseEntity::getId));
        assertEquals(3, rows.size());
        assertEquals(device.getId(), service.context(1L, rows.get(0).getId()).getDevice().getId());
        assertEquals("随支出新建的设备", service.context(1L, rows.get(1).getId()).getDevice().getName());
        assertNull(service.context(1L, rows.get(2).getId()).getDevice());
        assertEquals(new BigDecimal("42.50"), rows.getFirst().getTransactionAmt());
        assertEquals(new BigDecimal("100.00"), devices.selectById(device.getId()).getPurchasePrice());
    }

    @Test void 新增关联互斥且设备锁定时不写入支出() {
        long before = expenses.selectCount(null);
        var request = draftExpense(); request.setDeviceId(99L); request.setNewDevice(new DeviceCreateReq());
        assertThrows(ResponseStatusException.class, () -> service.createExpenses(1L, List.of(request)));
        request.setNewDevice(null);
        doThrow(new SecondaryLockRequiredException("/my-hub/device")).when(locks).checkMenus(1L, "/my-hub/device");
        assertThrows(SecondaryLockRequiredException.class, () -> service.createExpenses(1L, List.of(request)));
        assertEquals(before, expenses.selectCount(null));
        assertTrue(service.createExpenses(1L, List.of(draftExpense())));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void 新增关联失败回滚整笔支出和新设备且不可关联其他用户设备() {
        long expenseCount = expenses.selectCount(null), deviceCount = devices.selectCount(null);
        var request = draftExpense(); request.setDeviceId(Long.MAX_VALUE);
        assertThrows(ResponseStatusException.class, () -> service.createExpenses(1L, List.of(request)));
        assertEquals(expenseCount, expenses.selectCount(null));
        var transaction = new TransactionTemplate(transactions);
        var foreign = transaction.execute(status -> device(2L));
        try {
            request.setDeviceId(foreign.getId());
            assertThrows(ResponseStatusException.class, () -> service.createExpenses(1L, List.of(request)));
            assertEquals(expenseCount, expenses.selectCount(null));
            request.setDeviceId(null);
            var draft = new DeviceCreateReq(); draft.setName("不应保存的设备"); request.setNewDevice(draft);
            doThrow(new IllegalStateException("模拟关联写入失败")).when(links).bind(any(DeviceExpenseEntity.class));
            assertThrows(IllegalStateException.class, () -> service.createExpenses(1L, List.of(request)));
            assertEquals(expenseCount, expenses.selectCount(null));
            assertEquals(deviceCount + 1, devices.selectCount(null));
        } finally { transaction.executeWithoutResult(status -> devices.deleteById(foreign.getId())); }
    }

    private ExpenseCreateReq draftExpense() {
        var request = new ExpenseCreateReq();
        request.setAmt(new BigDecimal("42.50")); request.setExpTypeId(81L); request.setPayTypeId(82L);
        request.setExpTime(LocalDateTime.of(2026, 11, 12, 12, 0)); request.setRemark("新增关联测试");
        return request;
    }

    @Test void 多笔支出绑定同一设备并支持更换及反复解绑重绑() {
        var first = expense(1L); var second = expense(1L);
        var device = device(1L); var replacement = device(1L);
        service.bind(1L, first.getId(), device.getId());
        service.bind(1L, second.getId(), device.getId());
        assertEquals(device.getId(), service.context(1L, first.getId()).getDevice().getId());
        assertEquals(device.getId(), service.context(1L, second.getId()).getDevice().getId());
        var relation = links.selectOne(new LambdaQueryWrapper<DeviceExpenseEntity>().eq(DeviceExpenseEntity::getExpenseId, first.getId()));
        for (int i = 0; i < 3; i++) {
            service.unbind(1L, first.getId());
            assertNull(service.context(1L, first.getId()).getDevice());
            service.bind(1L, first.getId(), replacement.getId());
        }
        assertEquals(relation.getId(), links.selectOne(new LambdaQueryWrapper<DeviceExpenseEntity>()
                .eq(DeviceExpenseEntity::getExpenseId, first.getId())).getId());
        assertEquals(replacement.getId(), service.context(1L, first.getId()).getDevice().getId());
        assertEquals(new BigDecimal("100.00"), devices.selectById(replacement.getId()).getPurchasePrice());
        assertEquals(new BigDecimal("42.50"), expenses.selectById(first.getId()).getAmt());
    }

    @Test void 预填使用记账金额和业务日期并保留用户确认的修改() {
        var expense = expense(1L);
        var context = service.context(1L, expense.getId());
        assertEquals(new BigDecimal("42.50"), context.getDefaults().getPurchasePrice());
        assertEquals("2026-10-22", context.getDefaults().getPurchaseDate().toString());
        assertEquals("模拟耳机", context.getDefaults().getName());
        assertEquals("模拟商家", context.getDefaults().getPurchasePlace());
        assertEquals("模拟备注", context.getDefaults().getRemark());
        context.getDefaults().setName("修改后的名称");
        context.getDefaults().setPurchasePrice(new BigDecimal("50.00"));
        var device = service.createAndBind(1L, expense.getId(), context.getDefaults());
        assertEquals("修改后的名称", device.getName());
        assertEquals(new BigDecimal("50.00"), devices.selectById(device.getId()).getPurchasePrice());
        assertEquals(device.getId(), service.context(1L, expense.getId()).getDevice().getId());
    }

    @Test void 跨用户及已删除记录不可绑定() {
        var owned = expense(1L); var foreign = expense(2L);
        var ownedDevice = device(1L); var foreignDevice = device(2L);
        assertThrows(ResponseStatusException.class, () -> service.bind(1L, foreign.getId(), ownedDevice.getId()));
        assertThrows(ResponseStatusException.class, () -> service.bind(1L, owned.getId(), foreignDevice.getId()));
        assertThrows(ResponseStatusException.class, () -> service.context(1L, foreign.getId()));
        assertThrows(ResponseStatusException.class, () -> service.unbind(1L, foreign.getId()));
        service.deleteDevice(1L, ownedDevice.getId());
        assertThrows(ResponseStatusException.class, () -> service.bind(1L, owned.getId(), ownedDevice.getId()));
        assertNull(service.context(1L, owned.getId()).getDevice());
    }

    @Test void 重复新建拒绝且不生成多余设备() {
        var expense = expense(1L);
        var draft = service.context(1L, expense.getId()).getDefaults();
        service.createAndBind(1L, expense.getId(), draft);
        long count = devices.selectCount(null);
        assertThrows(ResponseStatusException.class, () -> service.createAndBind(1L, expense.getId(), draft));
        assertEquals(count, devices.selectCount(null));
    }

    @Test void 删除任一侧清理关联并保留另一侧及其他用户() {
        var first = expense(1L); var second = expense(1L); var foreign = expense(2L);
        var device = device(1L); var otherDevice = device(2L);
        service.bind(1L, first.getId(), device.getId());
        service.bind(1L, second.getId(), device.getId());
        service.bind(2L, foreign.getId(), otherDevice.getId());
        service.deleteExpenses(1L, List.of(first.getId(), foreign.getId()));
        assertNull(expenses.selectById(first.getId()));
        assertNotNull(expenses.selectById(foreign.getId()));
        assertNotNull(devices.selectById(device.getId()));
        assertTrue(links.findLinks(1L, List.of(first.getId())).isEmpty());
        service.deleteDevice(1L, device.getId());
        assertNotNull(expenses.selectById(second.getId()));
        assertNull(service.context(1L, second.getId()).getDevice());
        assertEquals(otherDevice.getId(), service.context(2L, foreign.getId()).getDevice().getId());
    }

    @Test void 账单列表显示关联且设备二级锁不泄露名称() {
        var expense = expense(1L); var device = device(1L);
        service.bind(1L, expense.getId(), device.getId());
        var row = RecordApiConvertor.INSTANCE.toExpenseVO(expense);
        service.enrich(1L, List.of(row));
        assertEquals(device.getId(), row.getDeviceId());
        assertEquals("模拟设备", row.getDeviceName());
        doThrow(new SecondaryLockRequiredException("/my-hub/device")).when(locks).checkMenus(1L, "/my-hub/device");
        var hidden = RecordApiConvertor.INSTANCE.toExpenseVO(expense);
        service.enrich(1L, List.of(hidden));
        assertNull(hidden.getDeviceName());
        assertNull(hidden.getDeviceId());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void 关联保存失败时新设备事务回滚() {
        var transaction = new TransactionTemplate(transactions);
        var expense = transaction.execute(status -> expense(1L));
        long before = devices.selectCount(null);
        try {
            doThrow(new IllegalStateException("模拟关联写入失败")).when(links).bind(any(DeviceExpenseEntity.class));
            var draft = new DeviceCreateReq(); draft.setName("不应保留的设备");
            assertThrows(IllegalStateException.class, () -> service.createAndBind(1L, expense.getId(), draft));
            assertEquals(before, devices.selectCount(null));
            assertTrue(links.findLinks(1L, List.of(expense.getId())).isEmpty());
        } finally {
            transaction.executeWithoutResult(status -> expenses.deleteById(expense.getId()));
        }
    }

    private ExpenseEntity expense(long userId) {
        var row = new ExpenseEntity();
        row.setUserId(userId); row.setExpTypeId(81L); row.setPayTypeId(82L); row.setAmt(new BigDecimal("42.50")); row.setTransactionAmt(new BigDecimal("60.00"));
        row.setExpTime(LocalDateTime.of(2026, 10, 22, 23, 30)); row.setExpDesc("模拟耳机");
        row.setCounterparty("模拟商家"); row.setRemark("模拟备注"); row.setIsDeleted(0);
        expenses.insert(row); return row;
    }
    private DeviceEntity device(long userId) {
        var row = new DeviceEntity(); row.setUserId(userId); row.setName("模拟设备");
        row.setPurchasePrice(new BigDecimal("100.00")); row.fillCreateCommonField(userId);
        devices.insert(row); return row;
    }
}
