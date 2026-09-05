package top.aiolife.record.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
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
public class TimeTrackerCategoryServiceImpl extends ServiceImpl<ITimeTrackerCategoryMapper, TimeTrackerCategoryEntity> implements ITimeTrackerCategoryService {

    @Override
    public List<TimeTrackerCategoryEntity> listUserVisibleCategories(Long userId) {
        // 1. 查询所有公共分类（未删除且已启用）
        List<TimeTrackerCategoryEntity> publicCategories = this.list(new LambdaQueryWrapper<TimeTrackerCategoryEntity>()
                .eq(TimeTrackerCategoryEntity::getUserId, 0L)
                .eq(TimeTrackerCategoryEntity::getIsDeleted, 0)
                .eq(TimeTrackerCategoryEntity::getIsEnabled, 1));

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
                // 如果存在覆盖记录且被标记为禁用，则隐藏该公共分类
                if (overrideRecord.getIsEnabled() != null && overrideRecord.getIsEnabled() == 0) {
                    continue;
                }
                // 否则，应用覆盖属性
                TimeTrackerCategoryEntity merged = new TimeTrackerCategoryEntity();
                merged.setId(publicCategory.getId());
                merged.setUserId(userId);
                merged.setTemplateId(publicCategory.getId());
                merged.setName(overrideRecord.getName() != null ? overrideRecord.getName() : publicCategory.getName());
                merged.setColor(overrideRecord.getColor() != null ? overrideRecord.getColor() : publicCategory.getColor());
                merged.setIcon(overrideRecord.getIcon() != null ? overrideRecord.getIcon() : publicCategory.getIcon());
                merged.setDescription(overrideRecord.getDescription() != null ? overrideRecord.getDescription() : publicCategory.getDescription());
                merged.setIsTrackTime(overrideRecord.getIsTrackTime() != null ? overrideRecord.getIsTrackTime() : publicCategory.getIsTrackTime());
                merged.setSort(overrideRecord.getSort() != null ? overrideRecord.getSort() : publicCategory.getSort());
                merged.setTimeType(overrideRecord.getTimeType() != null ? overrideRecord.getTimeType() : publicCategory.getTimeType());
                merged.setIsDeleted(0);
                merged.setIsEnabled(1);
                result.add(merged);
            } else {
                // 原样展示公共分类
                result.add(publicCategory);
            }
        }

        // 4. 添加用户纯原创的私有分类（未删除、已启用且没有 templateId）
        List<TimeTrackerCategoryEntity> privateCategories = userRecords.stream()
                .filter(record -> record.getTemplateId() == null && record.getIsDeleted() == 0 && (record.getIsEnabled() == null || record.getIsEnabled() == 1))
                .collect(Collectors.toList());
        result.addAll(privateCategories);

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

        if (disabledOverrides.isEmpty()) {
            return Collections.emptyList();
        }

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
        for (TimeTrackerCategoryEntity override : disabledOverrides) {
            TimeTrackerCategoryEntity publicCat = publicMap.get(override.getTemplateId());
            if (publicCat != null) {
                TimeTrackerCategoryEntity hidden = new TimeTrackerCategoryEntity();
                hidden.setId(publicCat.getId());
                hidden.setTemplateId(publicCat.getId());
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
        if (category.getUserId() != null && category.getUserId() == 0L) {
            throw new RuntimeException("普通用户不能创建公共分类");
        }
        
        category.setId(null);
        category.setUserId(userId);
        category.setTemplateId(null);
        category.setIsDeleted(0);
        if (category.getSort() == null) {
            category.setSort(nextSort(listUserVisibleCategories(userId)));
        }
        fillCategoryDefaults(category);
        
        this.save(category);
    }

    @Override
    public void updateCategory(Long categoryId, TimeTrackerCategoryEntity updates, Long userId) {
        TimeTrackerCategoryEntity target = this.getById(categoryId);
        if (target == null) {
            throw new RuntimeException("分类不存在");
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
            this.updateById(updates);
        } else {
            throw new RuntimeException("无权修改此分类");
        }
    }

    /**
     * 创建只包含变化字段的用户覆盖记录。
     *
     * <p>覆盖记录与公共分类共用一张表。sort、timeType 等覆盖字段必须允许为 null，
     * 否则数据库默认值会被误认为用户显式覆盖值。</p>
     */
    private TimeTrackerCategoryEntity buildOverrideRecord(TimeTrackerCategoryEntity template,
                                                           TimeTrackerCategoryEntity updates,
                                                           Long userId) {
        TimeTrackerCategoryEntity override = new TimeTrackerCategoryEntity();
        override.setUserId(userId);
        override.setTemplateId(template.getId());
        override.setName(changedValue(updates.getName(), template.getName()));
        override.setColor(changedValue(updates.getColor(), template.getColor()));
        override.setIcon(changedValue(updates.getIcon(), template.getIcon()));
        override.setDescription(changedValue(updates.getDescription(), template.getDescription()));
        override.setIsTrackTime(changedValue(updates.getIsTrackTime(), template.getIsTrackTime()));
        override.setSort(changedValue(updates.getSort(), template.getSort()));
        override.setIsEnabled(changedValue(updates.getIsEnabled(), template.getIsEnabled()));
        override.setTimeType(changedValue(updates.getTimeType(), template.getTimeType()));
        override.setCreateUser(userId);
        override.setUpdateUser(userId);
        override.setIsDeleted(0);
        return override;
    }

    /**
     * 只更新请求中出现的字段；字段恢复成公共值时显式写 null，继续继承公共分类。
     */
    private void updateOverrideRecord(Long overrideId,
                                      TimeTrackerCategoryEntity template,
                                      TimeTrackerCategoryEntity updates,
                                      Long userId) {
        LambdaUpdateWrapper<TimeTrackerCategoryEntity> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(TimeTrackerCategoryEntity::getId, overrideId);

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
            wrapper.set(TimeTrackerCategoryEntity::getIsTrackTime, changedValue(updates.getIsTrackTime(), template.getIsTrackTime()));
        }
        if (updates.getSort() != null) {
            wrapper.set(TimeTrackerCategoryEntity::getSort, changedValue(updates.getSort(), template.getSort()));
        }
        if (updates.getIsEnabled() != null) {
            wrapper.set(TimeTrackerCategoryEntity::getIsEnabled, changedValue(updates.getIsEnabled(), template.getIsEnabled()));
        }
        if (updates.getTimeType() != null) {
            wrapper.set(TimeTrackerCategoryEntity::getTimeType, changedValue(updates.getTimeType(), template.getTimeType()));
        }
        wrapper.set(TimeTrackerCategoryEntity::getUpdateUser, userId);
        wrapper.set(TimeTrackerCategoryEntity::getIsDeleted, 0);
        this.update(wrapper);
    }

    private <T> T changedValue(T value, T templateValue) {
        return value != null && !Objects.equals(value, templateValue) ? value : null;
    }

    @Override
    public void deleteCategory(Long categoryId, Long userId) {
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
        category.setId(null);
        category.setUserId(0L); // 强制为公共分类
        category.setTemplateId(null);
        category.setIsDeleted(0);
        if (category.getSort() == null) {
            category.setSort(nextSort(listAllCategories()));
        }
        fillCategoryDefaults(category);
        this.save(category);
    }

    private int nextSort(List<TimeTrackerCategoryEntity> categories) {
        return categories.stream()
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

    @Override
    public void adminUpdateCategory(Long categoryId, TimeTrackerCategoryEntity updates) {
        TimeTrackerCategoryEntity target = this.getById(categoryId);
        if (target == null || target.getUserId() != 0L) {
            throw new RuntimeException("只能更新公共分类");
        }
        updates.setId(categoryId);
        updates.setUserId(0L);
        updates.setTemplateId(null);
        this.updateById(updates);
    }

    @Override
    public void adminDeleteCategory(Long categoryId) {
        TimeTrackerCategoryEntity target = this.getById(categoryId);
        if (target == null || target.getUserId() != 0L) {
            throw new RuntimeException("只能删除公共分类");
        }
        
        // 物理删除公共分类
        this.removeById(categoryId);
        
        // （可选）同时删除所有用户的相关覆盖记录
        this.remove(new LambdaQueryWrapper<TimeTrackerCategoryEntity>()
                .eq(TimeTrackerCategoryEntity::getTemplateId, categoryId));
    }
}
