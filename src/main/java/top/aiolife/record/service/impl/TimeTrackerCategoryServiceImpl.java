package top.aiolife.record.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.aiolife.record.mapper.ITimeTrackerCategoryMapper;
import top.aiolife.record.pojo.entity.TimeTrackerCategoryEntity;
import top.aiolife.record.service.ITimeTrackerCategoryService;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 时间追踪-分类配置表(TimeTrackerCategory) Service 实现类
 *
 * @author Lys1313013
 * @since 2026-03-07
 */
@Service
@Transactional
public class TimeTrackerCategoryServiceImpl extends ServiceImpl<ITimeTrackerCategoryMapper, TimeTrackerCategoryEntity> implements ITimeTrackerCategoryService {
    @org.springframework.beans.factory.annotation.Autowired
    private top.aiolife.record.mapper.ITimeRecordMapper timeRecordMapper;

    /** 分类规模很小，按主键顺序锁定分类关系，避免并发移动/新增造成第三级或环。 */
    void lockHierarchy() {
        this.list(new LambdaQueryWrapper<TimeTrackerCategoryEntity>()
                .select(TimeTrackerCategoryEntity::getId)
                .orderByAsc(TimeTrackerCategoryEntity::getId).last("FOR UPDATE"));
    }

    private Long parentId(TimeTrackerCategoryEntity category) {
        return category.getParentId() == null ? 0L : category.getParentId();
    }

    /** 基于合并后的树校验，停用节点也参与层级检查。 */
    void validateParent(Long id, Long parentId, List<TimeTrackerCategoryEntity> categories) {
        if (parentId == 0L) return;
        if (Objects.equals(id, parentId)) throw new IllegalArgumentException("分类不能作为自己的上级");
        var parent = categories.stream().filter(c -> Objects.equals(c.getId(), parentId))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("上级分类不存在或无权使用"));
        if (parentId(parent) != 0L) throw new IllegalArgumentException("分类最多支持两级，请选择一级分类作为上级");
        if (id != null && categories.stream().anyMatch(c -> Objects.equals(parentId(c), id))) {
            throw new IllegalArgumentException("该分类已有子分类，不能移动到其他分类下");
        }
    }

    private void ensureNoChildren(Long id) {
        if (this.count(new LambdaQueryWrapper<TimeTrackerCategoryEntity>()
                .eq(TimeTrackerCategoryEntity::getParentId, id)) > 0) {
            throw new IllegalArgumentException("请先移动或删除子分类");
        }
    }

    private void ensureNoRecords(Long id) {
        if (timeRecordMapper.selectCount(new LambdaQueryWrapper<top.aiolife.record.pojo.entity.TimeRecordEntity>()
                .eq(top.aiolife.record.pojo.entity.TimeRecordEntity::getCategoryId, id)) > 0) {
            throw new IllegalArgumentException("该分类已有时迹记录，请停用或隐藏分类");
        }
    }


    @Override
    public List<TimeTrackerCategoryEntity> listUserVisibleCategories(Long userId) {
        return listUserCategories(userId).stream()
                .filter(c -> !Objects.equals(c.getIsEnabled(), 0)).toList();
    }

    @Override
    public List<TimeTrackerCategoryEntity> listUserCategories(Long userId) {
        // 1. 包含停用分类，保留历史记录元数据
        List<TimeTrackerCategoryEntity> publicCategories = this.list(new LambdaQueryWrapper<TimeTrackerCategoryEntity>()
                .eq(TimeTrackerCategoryEntity::getUserId, 0L)
                .eq(TimeTrackerCategoryEntity::getIsDeleted, 0));

        // 2. 查询当前用户的所有记录（包含禁用的，因为需要知道哪些公共分类被隐藏了）
        List<TimeTrackerCategoryEntity> userRecords = this.list(new LambdaQueryWrapper<TimeTrackerCategoryEntity>()
                .eq(TimeTrackerCategoryEntity::getUserId, userId));

        // 构建覆盖 Map: templateId -> CategoryOverride
        Map<Long, TimeTrackerCategoryEntity> overrideMap = userRecords.stream()
                .filter(record -> record.getTemplateId() != null)
                .collect(Collectors.toMap(TimeTrackerCategoryEntity::getTemplateId, record -> record, (existing, replacement) -> replacement));

        List<TimeTrackerCategoryEntity> result = new ArrayList<>();

        // 3. 处理公共分类
        for (TimeTrackerCategoryEntity publicCategory : publicCategories) {
            TimeTrackerCategoryEntity overrideRecord = overrideMap.get(publicCategory.getId());

            if (overrideRecord != null) {
                // 应用个人覆盖属性，公共停用仍优先。
                TimeTrackerCategoryEntity merged = new TimeTrackerCategoryEntity();
                merged.setId(publicCategory.getId());
                merged.setUserId(userId);
                merged.setTemplateId(publicCategory.getId());
                merged.setParentId(valueOrTemplate(overrideRecord.getParentId(), parentId(publicCategory)));
                merged.setName(overrideRecord.getName() != null ? overrideRecord.getName() : publicCategory.getName());
                merged.setColor(overrideRecord.getColor() != null ? overrideRecord.getColor() : publicCategory.getColor());
                merged.setIcon(overrideRecord.getIcon() != null ? overrideRecord.getIcon() : publicCategory.getIcon());
                merged.setDescription(overrideRecord.getDescription() != null ? overrideRecord.getDescription() : publicCategory.getDescription());
                merged.setIsTrackTime(overrideRecord.getIsTrackTime() != null ? overrideRecord.getIsTrackTime() : publicCategory.getIsTrackTime());
                merged.setSort(overrideRecord.getSort() != null ? overrideRecord.getSort() : publicCategory.getSort());
                merged.setTimeType(overrideRecord.getTimeType() != null ? overrideRecord.getTimeType() : publicCategory.getTimeType());
                merged.setIsDeleted(0);
                merged.setIsEnabled(Objects.equals(publicCategory.getIsEnabled(), 0) ? 0
                        : valueOrTemplate(overrideRecord.getIsEnabled(), 1));
                result.add(merged);
            } else {
                // 原样展示公共分类
                result.add(publicCategory);
            }
        }

        // 4. 添加用户原创分类（含停用分类）
        List<TimeTrackerCategoryEntity> privateCategories = userRecords.stream()
                .filter(record -> record.getTemplateId() == null && Objects.equals(record.getIsDeleted(), 0))
                .collect(Collectors.toList());
        result.addAll(privateCategories);

        // 父级停用仅影响有效状态，不改写子分类自己的配置，恢复父级即可恢复子级。
        Map<Long, TimeTrackerCategoryEntity> byId = result.stream()
                .collect(Collectors.toMap(TimeTrackerCategoryEntity::getId, c -> c));
        for (TimeTrackerCategoryEntity category : result) {
            if (parentId(category) != 0L) {
                var parent = byId.get(parentId(category));
                if (parent == null || Objects.equals(parent.getIsEnabled(), 0)) category.setIsEnabled(0);
            }
        }
        // 5. 按 sort 升序排列
        result.sort(Comparator.comparing(TimeTrackerCategoryEntity::getSort, Comparator.nullsLast(Integer::compareTo)));

        return result;
    }

    @Override
    public List<TimeTrackerCategoryEntity> listUserHiddenCategories(Long userId) {
        // 1. 查询当前用户所有禁用的覆盖记录（isEnabled = 0）
        List<TimeTrackerCategoryEntity> disabledOverrides = this.list(new LambdaQueryWrapper<TimeTrackerCategoryEntity>()
                .eq(TimeTrackerCategoryEntity::getUserId, userId)
                .eq(TimeTrackerCategoryEntity::getIsEnabled, 0)
                .isNotNull(TimeTrackerCategoryEntity::getTemplateId));

        List<TimeTrackerCategoryEntity> hiddenPrivate = this.list(new LambdaQueryWrapper<TimeTrackerCategoryEntity>()
                .eq(TimeTrackerCategoryEntity::getUserId, userId)
                .eq(TimeTrackerCategoryEntity::getIsEnabled, 0)
                .isNull(TimeTrackerCategoryEntity::getTemplateId));
        if (disabledOverrides.isEmpty()) return hiddenPrivate;

        // 2. 获取对应的公共分类信息
        Set<Long> templateIds = disabledOverrides.stream()
                .map(TimeTrackerCategoryEntity::getTemplateId)
                .collect(Collectors.toSet());

        List<TimeTrackerCategoryEntity> publicCategories = this.list(new LambdaQueryWrapper<TimeTrackerCategoryEntity>()
                .in(TimeTrackerCategoryEntity::getId, templateIds)
                .eq(TimeTrackerCategoryEntity::getUserId, 0L)
                .eq(TimeTrackerCategoryEntity::getIsDeleted, 0));

        Map<Long, TimeTrackerCategoryEntity> publicMap = publicCategories.stream()
                .collect(Collectors.toMap(TimeTrackerCategoryEntity::getId, c -> c));

        // 3. 构建隐藏分类列表
        List<TimeTrackerCategoryEntity> result = new ArrayList<>();
        result.addAll(hiddenPrivate);
        for (TimeTrackerCategoryEntity override : disabledOverrides) {
            TimeTrackerCategoryEntity publicCat = publicMap.get(override.getTemplateId());
            if (publicCat != null) {
                TimeTrackerCategoryEntity hidden = new TimeTrackerCategoryEntity();
                hidden.setId(publicCat.getId());
                hidden.setTemplateId(publicCat.getId());
                hidden.setParentId(valueOrTemplate(override.getParentId(), parentId(publicCat)));
                hidden.setName(override.getName() != null ? override.getName() : publicCat.getName());
                hidden.setColor(override.getColor() != null ? override.getColor() : publicCat.getColor());
                hidden.setIcon(override.getIcon() != null ? override.getIcon() : publicCat.getIcon());
                hidden.setDescription(override.getDescription() != null ? override.getDescription() : publicCat.getDescription());
                hidden.setIsTrackTime(override.getIsTrackTime() != null ? override.getIsTrackTime() : publicCat.getIsTrackTime());
                hidden.setIsEnabled(0);
                result.add(hidden);
            }
        }

        return result;
    }

    @Override
    public void createCategory(TimeTrackerCategoryEntity category, Long userId) {
        if (parentId(category) != 0L) lockHierarchy();
        if (category.getUserId() != null && category.getUserId() == 0L) {
            throw new RuntimeException("普通用户不能创建公共分类");
        }
        
        category.setId(null);
        category.setUserId(userId);
        category.setTemplateId(null);
        category.setIsDeleted(0);
        category.setParentId(parentId(category));
        if (category.getParentId() != 0L) validateParent(category.getId(), category.getParentId(), listUserCategories(userId));
        if (category.getSort() == null) {
            category.setSort(nextSort(listUserVisibleCategories(userId), category.getParentId()));
        }
        fillCategoryDefaults(category);
        
        this.save(category);
    }

    @Override
    public void updateCategory(Long categoryId, TimeTrackerCategoryEntity updates, Long userId) {
        if (updates.isParentIdSpecified()) lockHierarchy();
        TimeTrackerCategoryEntity target = this.getById(categoryId);
        if (target == null) {
            throw new RuntimeException("分类不存在");
        }

        if (!Objects.equals(target.getUserId(), 0L) && !Objects.equals(target.getUserId(), userId)) {
            throw new IllegalArgumentException("无权修改此分类");
        }
        // 对外始终使用公共分类 ID，拒绝绕过合并语义直接修改覆盖记录。
        if (target.getTemplateId() != null) {
            throw new IllegalArgumentException("请使用公共分类ID修改覆盖设置");
        }
        if (updates.isParentIdSpecified()) {
            Long desired = updates.getParentId() == null && target.getUserId() == 0L
                    ? parentId(target) : parentId(updates);
            validateParent(categoryId, desired, listUserCategories(userId));
            if (target.getUserId() != 0L) updates.setParentId(desired);
        }

        if (target.getUserId() == 0L) {
            // 目标是公共分类，生成覆盖记录
            // 先检查是否已经存在覆盖记录
            TimeTrackerCategoryEntity existingOverride = this.getOne(new LambdaQueryWrapper<TimeTrackerCategoryEntity>()
                    .eq(TimeTrackerCategoryEntity::getUserId, userId)
                    .eq(TimeTrackerCategoryEntity::getTemplateId, categoryId)
                    .last("LIMIT 1"));

            if (existingOverride != null) {
                updateOverrideRecord(existingOverride.getId(), target, updates, userId);
            } else {
                this.save(buildOverrideRecord(target, updates, userId));
            }
        } else if (target.getUserId().equals(userId)) {
            // 目标是当前用户的记录（私有分类或已有的覆盖记录），直接更新
            updates.setId(categoryId);
            updates.setUserId(userId);
            updates.setTemplateId(target.getTemplateId());
            updates.setCreateUser(target.getCreateUser());
            updates.setCreateTime(target.getCreateTime());
            updates.setIsDeleted(target.getIsDeleted());
            updates.fillUpdateCommonField(userId);
            this.update(updates, new LambdaQueryWrapper<TimeTrackerCategoryEntity>()
                    .eq(TimeTrackerCategoryEntity::getId, categoryId)
                    .eq(TimeTrackerCategoryEntity::getUserId, userId));
        } else {
            throw new RuntimeException("无权修改此分类");
        }
    }

    /**
     * 创建用户覆盖记录。可空的展示字段只保存变化值，非空业务字段保存当前有效值。
     */
    private TimeTrackerCategoryEntity buildOverrideRecord(TimeTrackerCategoryEntity template,
                                                           TimeTrackerCategoryEntity updates,
                                                           Long userId) {
        TimeTrackerCategoryEntity override = new TimeTrackerCategoryEntity();
        override.setUserId(userId);
        override.setTemplateId(template.getId());
        override.setParentId(updates.isParentIdSpecified() ? updates.getParentId() : null);
        override.setName(changedValue(updates.getName(), template.getName()));
        override.setColor(changedValue(updates.getColor(), template.getColor()));
        override.setIcon(changedValue(updates.getIcon(), template.getIcon()));
        override.setDescription(changedValue(updates.getDescription(), template.getDescription()));
        override.setIsTrackTime(valueOrTemplate(updates.getIsTrackTime(), template.getIsTrackTime()));
        override.setSort(valueOrTemplate(updates.getSort(), template.getSort()));
        override.setIsEnabled(valueOrTemplate(updates.getIsEnabled(), template.getIsEnabled()));
        override.setTimeType(valueOrTemplate(updates.getTimeType(), template.getTimeType()));
        override.setCreateUser(userId);
        override.setUpdateUser(userId);
        override.setIsDeleted(0);
        return override;
    }

    /**
     * 只更新请求中出现的字段。非空业务字段直接保存有效值。
     */
    private void updateOverrideRecord(Long overrideId,
                                      TimeTrackerCategoryEntity template,
                                      TimeTrackerCategoryEntity updates,
                                      Long userId) {
        LambdaUpdateWrapper<TimeTrackerCategoryEntity> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(TimeTrackerCategoryEntity::getId, overrideId);

        if (updates.isParentIdSpecified()) {
            wrapper.set(TimeTrackerCategoryEntity::getParentId, updates.getParentId());
        }
        if (updates.getName() != null) {
            wrapper.set(TimeTrackerCategoryEntity::getName, changedValue(updates.getName(), template.getName()));
        }
        if (updates.getColor() != null) {
            wrapper.set(TimeTrackerCategoryEntity::getColor, changedValue(updates.getColor(), template.getColor()));
        }
        if (updates.getIcon() != null) {
            wrapper.set(TimeTrackerCategoryEntity::getIcon, changedValue(updates.getIcon(), template.getIcon()));
        }
        if (updates.getDescription() != null) {
            wrapper.set(TimeTrackerCategoryEntity::getDescription, changedValue(updates.getDescription(), template.getDescription()));
        }
        if (updates.getIsTrackTime() != null) {
            wrapper.set(TimeTrackerCategoryEntity::getIsTrackTime, updates.getIsTrackTime());
        }
        if (updates.getSort() != null) {
            wrapper.set(TimeTrackerCategoryEntity::getSort, updates.getSort());
        }
        if (updates.getIsEnabled() != null) {
            wrapper.set(TimeTrackerCategoryEntity::getIsEnabled, updates.getIsEnabled());
        }
        if (updates.getTimeType() != null) {
            wrapper.set(TimeTrackerCategoryEntity::getTimeType, updates.getTimeType());
        }
        wrapper.set(TimeTrackerCategoryEntity::getUpdateUser, userId);
        wrapper.set(TimeTrackerCategoryEntity::getIsDeleted, 0);
        this.update(wrapper);
    }

    private <T> T changedValue(T value, T templateValue) {
        return value != null && !Objects.equals(value, templateValue) ? value : null;
    }

    private <T> T valueOrTemplate(T value, T templateValue) {
        return value != null ? value : templateValue;
    }

    @Override
    public void deleteCategory(Long categoryId, Long userId) {
        lockHierarchy();
        TimeTrackerCategoryEntity target = this.getById(categoryId);
        if (target == null) {
            throw new RuntimeException("分类不存在");
        }

        if (target.getUserId() == 0L) {
            // 目标是公共分类，生成隐藏记录
            TimeTrackerCategoryEntity existingOverride = this.getOne(new LambdaQueryWrapper<TimeTrackerCategoryEntity>()
                    .eq(TimeTrackerCategoryEntity::getUserId, userId)
                    .eq(TimeTrackerCategoryEntity::getTemplateId, categoryId)
                    .last("LIMIT 1"));

            if (existingOverride != null) {
                // 更新已有覆盖记录的 isEnabled = 0
                TimeTrackerCategoryEntity update = new TimeTrackerCategoryEntity();
                update.setId(existingOverride.getId());
                update.setIsEnabled(0);
                this.updateById(update);
            } else {
                // 插入隐藏记录
                TimeTrackerCategoryEntity hideRecord = new TimeTrackerCategoryEntity();
                hideRecord.setUserId(userId);
                hideRecord.setTemplateId(categoryId);
                hideRecord.setName(target.getName());
                hideRecord.setColor(target.getColor());
                hideRecord.setIcon(target.getIcon());
                hideRecord.setCreateUser(userId);
                hideRecord.setUpdateUser(userId);
                hideRecord.setIsEnabled(0);
                this.save(hideRecord);
            }
        } else if (target.getUserId().equals(userId)) {
            ensureNoChildren(categoryId);
            ensureNoRecords(categoryId);
            // 目标是当前用户的记录，标记删除
            TimeTrackerCategoryEntity update = new TimeTrackerCategoryEntity();
            update.setId(categoryId);
            update.setIsDeleted(1);
            this.updateById(update);
        } else {
            throw new RuntimeException("无权删除此分类");
        }
    }

    @Override
    public List<TimeTrackerCategoryEntity> listAllCategories() {
        return this.list(new LambdaQueryWrapper<TimeTrackerCategoryEntity>()
                .eq(TimeTrackerCategoryEntity::getUserId, 0L)
                .eq(TimeTrackerCategoryEntity::getIsDeleted, 0)
                .orderByAsc(TimeTrackerCategoryEntity::getSort));
    }

    @Override
    public void adminCreateCategory(TimeTrackerCategoryEntity category) {
        if (parentId(category) != 0L) lockHierarchy();
        category.setId(null);
        category.setUserId(0L); // 强制为公共分类
        category.setTemplateId(null);
        category.setIsDeleted(0);
        category.setParentId(parentId(category));
        validateParent(null, category.getParentId(), listAllCategories());
        if (category.getParentId() != 0L) validatePublicParentChange(null, category.getParentId());
        if (category.getSort() == null) {
            category.setSort(nextSort(listAllCategories(), category.getParentId()));
        }
        fillCategoryDefaults(category);
        this.save(category);
    }

    private int nextSort(List<TimeTrackerCategoryEntity> categories, Long parentId) {
        return categories.stream()
                .filter(c -> Objects.equals(parentId(c), parentId))
                .map(TimeTrackerCategoryEntity::getSort)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .map(maxSort -> maxSort + 10)
                .orElse(0);
    }

    private void fillCategoryDefaults(TimeTrackerCategoryEntity category) {
        if (category.getIsTrackTime() == null) {
            category.setIsTrackTime(0);
        }
        if (category.getIsEnabled() == null) {
            category.setIsEnabled(1);
        }
        if (category.getTimeType() == null) {
            category.setTimeType(1);
        }
    }

    /** 公共分类新增子级或移动时，也不能破坏个人覆盖后的两级结构。 */
    private void validatePublicParentChange(Long categoryId, Long desiredParent) {
        var owners = this.list(new LambdaQueryWrapper<TimeTrackerCategoryEntity>()
                .ne(TimeTrackerCategoryEntity::getUserId, 0L)).stream()
                .map(TimeTrackerCategoryEntity::getUserId).collect(Collectors.toSet());
        for (Long owner : owners) {
            if (categoryId != null) {
                var overrides = this.list(new LambdaQueryWrapper<TimeTrackerCategoryEntity>()
                        .eq(TimeTrackerCategoryEntity::getUserId, owner)
                        .eq(TimeTrackerCategoryEntity::getTemplateId, categoryId));
                if (overrides.stream().anyMatch(c -> c.getParentId() != null)) continue;
            }
            validateParent(categoryId, desiredParent, listUserCategories(owner));
        }
    }

    @Override
    public void adminUpdateCategory(Long categoryId, TimeTrackerCategoryEntity updates) {
        if (updates.isParentIdSpecified()) lockHierarchy();
        TimeTrackerCategoryEntity target = this.getById(categoryId);
        if (target == null || target.getUserId() != 0L) {
            throw new RuntimeException("只能更新公共分类");
        }
        if (updates.isParentIdSpecified() && !Objects.equals(parentId(updates), parentId(target))) {
            validateParent(categoryId, parentId(updates), listAllCategories());
            validatePublicParentChange(categoryId, parentId(updates));
            updates.setParentId(parentId(updates));
        }
        if (updates.isParentIdSpecified()) updates.setParentId(parentId(updates));
        updates.setId(categoryId);
        updates.setUserId(0L);
        updates.setTemplateId(null);
        this.updateById(updates);
    }

    @Override
    public void adminDeleteCategory(Long categoryId) {
        lockHierarchy();
        TimeTrackerCategoryEntity target = this.getById(categoryId);
        if (target == null || target.getUserId() != 0L) {
            throw new RuntimeException("只能删除公共分类");
        }
        
        ensureNoChildren(categoryId);
        ensureNoRecords(categoryId);
        // 删除公共分类
        this.removeById(categoryId);
        
        // （可选）同时删除所有用户的相关覆盖记录
        this.remove(new LambdaQueryWrapper<TimeTrackerCategoryEntity>()
                .eq(TimeTrackerCategoryEntity::getTemplateId, categoryId));
    }
}
