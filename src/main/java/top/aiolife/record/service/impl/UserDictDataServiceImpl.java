package top.aiolife.record.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import top.aiolife.core.lock.DistributedLockExecutor;
import top.aiolife.core.mybatis.IlikeQuery;
import top.aiolife.core.resq.PageResp;
import top.aiolife.core.util.SysUtil;
import top.aiolife.record.mapper.UserDictDataMapper;
import top.aiolife.record.pojo.entity.UserDictDataEntity;
import top.aiolife.record.pojo.vo.UserDictDataSortVO;
import top.aiolife.record.service.UserDictDataService;
import top.aiolife.record.service.UserDictDataCache;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 用户字典数据服务实现类
 *
 * @author Lys
 */
@Service
@RequiredArgsConstructor
public class UserDictDataServiceImpl extends ServiceImpl<UserDictDataMapper, UserDictDataEntity> implements UserDictDataService {

    private final UserDictDataCache dictDataCache;
    private final DistributedLockExecutor locks;

    @Override
    public List<UserDictDataEntity> listUserVisibleDictData(Long userId, String dictType) {
        return listUserVisibleDictData(userId, dictType, false);
    }

    @Override
    public List<UserDictDataEntity> listUserVisibleDictData(Long userId, String dictType, boolean includeDisabled) {
        // 1. 查询所有公共分类（user_id 为空，未删除）
        List<UserDictDataEntity> publicCategories = dictDataCache.list(null, dictType);

        // 2. 缓存包含停用项的原始记录，两种展示模式使用同一份数据，避免状态互相污染。
        List<UserDictDataEntity> userRecords = dictDataCache.list(userId, dictType);

        // 构建覆盖 Map: templateId -> CategoryOverride
        Map<Long, UserDictDataEntity> overrideMap = userRecords.stream()
                .filter(record -> record.getTemplateId() != null)
                .collect(Collectors.toMap(UserDictDataEntity::getTemplateId, record -> record, (existing, replacement) -> replacement));

        List<UserDictDataEntity> result = new ArrayList<>();

        // 3. 处理公共分类
        for (UserDictDataEntity publicCategory : publicCategories) {
            UserDictDataEntity overrideRecord = overrideMap.get(publicCategory.getId());

            if (overrideRecord != null) {
                // 业务页（includeDisabled=false）：如果覆盖记录标记为停用，则隐藏该公共分类。
                if (!includeDisabled && isDisabled(overrideRecord.getIsEnabled())) {
                    continue;
                }
                // 应用覆盖属性
                UserDictDataEntity merged = new UserDictDataEntity();
                merged.setId(publicCategory.getId()); // 返回给前端的 ID 仍是公共分类 ID，便于前端统一更新
                merged.setUserId(userId);
                merged.setTemplateId(publicCategory.getId());
                merged.setDictType(publicCategory.getDictType());
                boolean readonly = Integer.valueOf(1).equals(publicCategory.getIsReadonly());

                merged.setDictLabel((!readonly && overrideRecord.getDictLabel() != null) ? overrideRecord.getDictLabel() : publicCategory.getDictLabel());
                merged.setDictValue((!readonly && overrideRecord.getDictValue() != null) ? overrideRecord.getDictValue() : publicCategory.getDictValue());
                merged.setColor((!readonly && overrideRecord.getColor() != null) ? overrideRecord.getColor() : publicCategory.getColor());
                merged.setIcon((!readonly && overrideRecord.getIcon() != null) ? overrideRecord.getIcon() : publicCategory.getIcon());
                merged.setExtData((!readonly && overrideRecord.getExtData() != null) ? overrideRecord.getExtData() : publicCategory.getExtData());
                merged.setSortOrder((!readonly && overrideRecord.getSortOrder() != null) ? overrideRecord.getSortOrder() : publicCategory.getSortOrder());
                // 保留真实状态：覆盖记录的 isEnabled 优先；管理页需要看到停用项
                merged.setIsEnabled(overrideRecord.getIsEnabled() != null ? overrideRecord.getIsEnabled() : publicCategory.getIsEnabled());
                merged.setIsReadonly(publicCategory.getIsReadonly());
                result.add(merged);
            } else {
                // 没有覆盖记录
                if (!includeDisabled && isDisabled(publicCategory.getIsEnabled())) {
                    continue;
                }
                // 原样展示公共分类
                result.add(publicCategory);
            }
        }

        // 4. 添加用户纯原创的私有分类；NULL 未显式停用，按启用处理。
        List<UserDictDataEntity> privateCategories = userRecords.stream()
                .filter(record -> record.getTemplateId() == null
                        && (includeDisabled || !isDisabled(record.getIsEnabled())))
                .collect(Collectors.toList());
        result.addAll(privateCategories);

        // 5. 以 ID 打破相同 sort 的并列，确保内存分页在缓存返回顺序变化时仍稳定。
        result.sort(Comparator.comparing(UserDictDataEntity::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(UserDictDataEntity::getId, Comparator.nullsLast(Long::compareTo)));

        return result;
    }

    @Override
    public PageResp<UserDictDataEntity> pageVisibleDictData(long userId, String dictType, String dictLabel,
                                                            Integer isEnabled, int page, int pageSize) {
        // 管控页需要看到所有记录（含已停用），方便用户改回启用或删除
        List<UserDictDataEntity> dataList = listUserVisibleDictData(userId, dictType, true);

        if (SysUtil.isNotEmpty(dictLabel) || isEnabled != null) {
            dataList = dataList.stream().filter(item -> {
                boolean match = true;
                if (SysUtil.isNotEmpty(dictLabel)) {
                    match = item.getDictLabel() != null && item.getDictLabel().contains(dictLabel);
                }
                if (match && isEnabled != null) {
                    match = isEnabled.equals(item.getIsEnabled());
                }
                return match;
            }).toList();
        }

        int total = dataList.size();
        long offset = ((long) page - 1) * pageSize;
        List<UserDictDataEntity> pageList = new ArrayList<>();
        if (offset < total) {
            int fromIndex = Math.toIntExact(offset);
            int toIndex = (int) Math.min(offset + pageSize, total);
            pageList = dataList.subList(fromIndex, toIndex);
        }
        return PageResp.of(pageList, (long) total);
    }

    @Override
    public UserDictDataEntity getAccessibleById(long userId, long id) {
        return this.getOne(new LambdaQueryWrapper<UserDictDataEntity>()
                .eq(UserDictDataEntity::getId, id)
                .and(w -> w.eq(UserDictDataEntity::getUserId, userId)
                        .or().isNull(UserDictDataEntity::getUserId)));
    }

    @Override
    public PageResp<UserDictDataEntity> pageBaseDictData(String dictType, String dictLabel,
                                                         Integer isEnabled, int page, int pageSize) {
        LambdaQueryWrapper<UserDictDataEntity> wrapper = new LambdaQueryWrapper<>();
        // 强制只能查询基础值（user_id 为空）
        wrapper.isNull(UserDictDataEntity::getUserId);
        wrapper.eq(SysUtil.isNotEmpty(dictType), UserDictDataEntity::getDictType, dictType);
        IlikeQuery.anyOf(wrapper, SysUtil.isNotEmpty(dictLabel), dictLabel, UserDictDataEntity::getDictLabel);
        wrapper.eq(isEnabled != null, UserDictDataEntity::getIsEnabled, isEnabled);
        wrapper.orderByAsc(UserDictDataEntity::getDictType,
                UserDictDataEntity::getSortOrder,
                UserDictDataEntity::getId);
        var result = this.page(new Page<>(page, pageSize), wrapper);
        return PageResp.of(result.getRecords(), result.getTotal());
    }

    /** NULL 表示继承基础值，未显式停用即视为启用。 */
    private static boolean isDisabled(Integer isEnabled) {
        return Integer.valueOf(0).equals(isEnabled);
    }

    private void rejectBankTag(String type) {
        if ("bank_card_tag".equals(type)) throw new IllegalArgumentException("请在银行卡页面管理标签");
    }

    @Override
    public void createDictData(UserDictDataEntity entity, Long userId) {
        rejectBankTag(entity.getDictType());
        entity.setId(null);
        entity.setUserId(userId);
        entity.setTemplateId(null);
        entity.setIsDeleted(0);
        entity.fillCreateCommonField(userId);
        
        this.save(entity);
        dictDataCache.evictAfterCommit(userId, entity.getDictType());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDictData(Long id, UserDictDataEntity updates, Long userId) {
        rejectBankTag(updates.getDictType());
        UserDictDataEntity target = this.getById(id);
        if (target == null) {
            throw new RuntimeException("字典数据不存在");
        }

        rejectBankTag(target.getDictType());
        if (target.getUserId() == null) {
            // 只读公共项只允许改状态，写入内容在加锁前即可确定。
            if (Integer.valueOf(1).equals(target.getIsReadonly())) {
                UserDictDataEntity onlyStatusUpdate = new UserDictDataEntity();
                onlyStatusUpdate.setIsEnabled(updates.getIsEnabled());
                updates = onlyStatusUpdate;
            }
            UserDictDataEntity applied = updates;
            // 覆盖锁按用户与公共项隔离，避免并发首次覆盖各插入一条记录。
            if (!locks.tryRun(overrideLockKey(userId, id), () -> savePublicOverride(id, applied, userId))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "字典正在更新，请稍后重试");
            }
        } else if (target.getUserId().equals(userId)) {
            // 目标是当前用户的记录（私有分类或已有的覆盖记录），直接更新
            updates.setId(id);
            updates.setUserId(userId);
            updates.setTemplateId(target.getTemplateId());
            updates.setCreateUser(target.getCreateUser());
            updates.setCreateTime(target.getCreateTime());
            updates.setIsDeleted(target.getIsDeleted());
            updates.fillUpdateCommonField(userId);
            this.update(updates, new LambdaQueryWrapper<UserDictDataEntity>()
                    .eq(UserDictDataEntity::getId, id)
                    .eq(UserDictDataEntity::getUserId, userId));
        } else {
            throw new RuntimeException("无权修改此字典数据");
        }
        dictDataCache.evictAfterCommit(userId, target.getDictType(), updates.getDictType());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDictData(Long id, Long userId) {
        UserDictDataEntity target = this.getById(id);
        if (target == null) {
            throw new RuntimeException("字典数据不存在");
        }

        rejectBankTag(target.getDictType());
        if (target.getUserId() == null) {
            // 覆盖锁按用户与公共项隔离，避免并发首次隐藏各插入一条停用记录。
            if (!locks.tryRun(overrideLockKey(userId, id), () -> hidePublicDictData(id, target, userId))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "字典正在更新，请稍后重试");
            }
        } else if (target.getUserId().equals(userId)) {
            // 目标是当前用户的记录，直接物理删除或逻辑删除（已有 is_deleted 字段，使用 removeById 会触发逻辑删除）
            this.removeById(id);
        } else {
            throw new RuntimeException("无权删除此字典数据");
        }
        dictDataCache.evictAfterCommit(userId, target.getDictType());
    }

    /** 覆盖写入只与同一用户、同一公共项冲突，锁粒度取到用户与预设。 */
    private static String overrideLockKey(Long userId, Long templateId) {
        return "record:user-dict:override:" + userId + ":" + templateId;
    }

    /** 持锁期间读取并写入当前用户对公共字典的覆盖记录。 */
    private void savePublicOverride(Long templateId, UserDictDataEntity updates, Long userId) {
        UserDictDataEntity existingOverride = this.getOne(new LambdaQueryWrapper<UserDictDataEntity>()
                .eq(UserDictDataEntity::getUserId, userId)
                .eq(UserDictDataEntity::getTemplateId, templateId)
                .eq(UserDictDataEntity::getIsDeleted, 0)
                .last("LIMIT 1"));

        if (existingOverride != null) {
            updates.setId(existingOverride.getId());
            updates.setUserId(userId);
            updates.setTemplateId(templateId);
            updates.fillUpdateCommonField(userId);
            this.updateById(updates);
        } else {
            updates.setId(null);
            updates.setUserId(userId);
            updates.setTemplateId(templateId);
            updates.fillCreateCommonField(userId);
            this.save(updates);
        }
    }

    /** 持锁期间生成或更新当前用户对公共字典的停用记录。 */
    private void hidePublicDictData(Long templateId, UserDictDataEntity target, Long userId) {
        UserDictDataEntity existingOverride = this.getOne(new LambdaQueryWrapper<UserDictDataEntity>()
                .eq(UserDictDataEntity::getUserId, userId)
                .eq(UserDictDataEntity::getTemplateId, templateId)
                .eq(UserDictDataEntity::getIsDeleted, 0)
                .last("LIMIT 1"));

        if (existingOverride != null) {
            UserDictDataEntity update = new UserDictDataEntity();
            update.setId(existingOverride.getId());
            update.setIsEnabled(0);
            update.fillUpdateCommonField(userId);
            this.updateById(update);
        } else {
            UserDictDataEntity hideRecord = new UserDictDataEntity();
            hideRecord.setUserId(userId);
            hideRecord.setTemplateId(templateId);
            hideRecord.setDictType(target.getDictType());
            hideRecord.setDictLabel(target.getDictLabel());
            hideRecord.setDictValue(target.getDictValue());
            hideRecord.setColor(target.getColor());
            hideRecord.setIcon(target.getIcon());
            hideRecord.setIsEnabled(0);
            hideRecord.fillCreateCommonField(userId);
            this.save(hideRecord);
        }
    }

    @Override
    public boolean createBaseDictData(UserDictDataEntity entity) {
        entity.setUserId(null);
        entity.fillCreateCommonField(null);
        boolean saved = this.save(entity);
        if (saved) dictDataCache.evictAfterCommit(null, entity.getDictType());
        return saved;
    }

    @Override
    public boolean updateBaseDictData(Long id, UserDictDataEntity updates) {
        UserDictDataEntity target = this.getById(id);
        if (target == null || target.getUserId() != null) {
            throw new RuntimeException("只能修改基础值");
        }
        updates.setId(id);
        updates.fillUpdateCommonField(null);
        boolean updated = this.updateById(updates);
        if (updated) dictDataCache.evictAfterCommit(null, target.getDictType(), updates.getDictType());
        return updated;
    }

    @Override
    public boolean deleteBaseDictData(Long id) {
        UserDictDataEntity target = this.getById(id);
        if (target == null || target.getUserId() != null) {
            throw new RuntimeException("只能删除基础值");
        }
        boolean deleted = this.removeById(id);
        if (deleted) dictDataCache.evictAfterCommit(null, target.getDictType());
        return deleted;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<UserDictDataSortVO> reSortBaseDictData(String dictType, Long dragId, Long targetId, String position) {
        if (dictType == null || dictType.isBlank()) {
            throw new RuntimeException("字典类型不能为空");
        }
        if (dragId == null || targetId == null) {
            throw new RuntimeException("拖拽记录和目标记录不能为空");
        }
        if (!"before".equals(position) && !"after".equals(position)) {
            throw new RuntimeException("拖拽位置不正确");
        }
        if (dragId.equals(targetId)) {
            return List.of();
        }

        List<UserDictDataEntity> dictDataList = this.list(
                new LambdaQueryWrapper<UserDictDataEntity>()
                        .isNull(UserDictDataEntity::getUserId)
                        .eq(UserDictDataEntity::getDictType, dictType)
                        .orderByAsc(UserDictDataEntity::getSortOrder, UserDictDataEntity::getId)
                        .last("FOR UPDATE"));

        UserDictDataEntity draggedItem = dictDataList.stream()
                .filter(item -> dragId.equals(item.getId()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("被拖拽的字典数据不存在"));
        UserDictDataEntity targetItem = dictDataList.stream()
                .filter(item -> targetId.equals(item.getId()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("目标字典数据不存在"));

        dictDataList.remove(draggedItem);
        int targetIndex = dictDataList.indexOf(targetItem);
        int insertIndex = targetIndex + ("after".equals(position) ? 1 : 0);
        dictDataList.add(insertIndex, draggedItem);

        List<UserDictDataEntity> updates = new ArrayList<>();
        for (int index = 0; index < dictDataList.size(); index++) {
            UserDictDataEntity item = dictDataList.get(index);
            int nextSort = index * 10;
            if (!Objects.equals(item.getSortOrder(), nextSort)) {
                UserDictDataEntity update = new UserDictDataEntity();
                update.setId(item.getId());
                update.setSortOrder(nextSort);
                update.fillUpdateCommonField(null);
                updates.add(update);
            }
            item.setSortOrder(nextSort);
        }

        if (!updates.isEmpty() && !this.updateBatchById(updates)) {
            throw new RuntimeException("字典排序更新失败");
        }

        dictDataCache.evictAfterCommit(null, dictType);
        return dictDataList.stream()
                .map(item -> new UserDictDataSortVO(item.getId(), item.getSortOrder()))
                .toList();
    }
}
