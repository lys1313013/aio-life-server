package top.aiolife.record.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import top.aiolife.record.mapper.UserDictDataMapper;
import top.aiolife.record.pojo.entity.UserDictDataEntity;
import top.aiolife.record.service.UserDictDataService;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 用户字典数据服务实现类
 *
 * @author Lys
 */
@Service
public class UserDictDataServiceImpl extends ServiceImpl<UserDictDataMapper, UserDictDataEntity> implements UserDictDataService {

    @Override
    public List<UserDictDataEntity> listUserVisibleDictData(Long userId, String dictType) {
        return listUserVisibleDictData(userId, dictType, false);
    }

    @Override
    public List<UserDictDataEntity> listUserVisibleDictData(Long userId, String dictType, boolean includeDisabled) {
        // 1. 查询所有公共分类（userId = 0L，未删除）
        List<UserDictDataEntity> publicCategories = this.list(new LambdaQueryWrapper<UserDictDataEntity>()
                .eq(UserDictDataEntity::getUserId, 0L)
                .eq(UserDictDataEntity::getDictType, dictType)
                .eq(UserDictDataEntity::getIsDeleted, 0));

        // 2. 查询当前用户的所有记录（管理页需要看到停用项，业务页也需借此得知哪些公共分类被隐藏）
        List<UserDictDataEntity> userRecords = this.list(new LambdaQueryWrapper<UserDictDataEntity>()
                .eq(UserDictDataEntity::getUserId, userId)
                .eq(UserDictDataEntity::getDictType, dictType)
                .eq(UserDictDataEntity::getIsDeleted, 0));

        // 构建覆盖 Map: templateId -> CategoryOverride
        Map<Long, UserDictDataEntity> overrideMap = userRecords.stream()
                .filter(record -> record.getTemplateId() != null)
                .collect(Collectors.toMap(UserDictDataEntity::getTemplateId, record -> record, (existing, replacement) -> replacement));

        List<UserDictDataEntity> result = new ArrayList<>();

        // 3. 处理公共分类
        for (UserDictDataEntity publicCategory : publicCategories) {
            UserDictDataEntity overrideRecord = overrideMap.get(publicCategory.getId());

            if (overrideRecord != null) {
                // 业务页（includeDisabled=false）：如果覆盖记录标记为停用，则隐藏该公共分类
                if (!includeDisabled && "1".equals(overrideRecord.getStatus())) {
                    continue;
                }
                // 应用覆盖属性
                UserDictDataEntity merged = new UserDictDataEntity();
                merged.setId(publicCategory.getId()); // 返回给前端的 ID 仍是公共分类 ID，便于前端统一更新
                merged.setUserId(userId);
                merged.setTemplateId(publicCategory.getId());
                merged.setDictType(publicCategory.getDictType());
                boolean readonly = "Y".equals(publicCategory.getIsReadonly());

                merged.setDictLabel((!readonly && overrideRecord.getDictLabel() != null) ? overrideRecord.getDictLabel() : publicCategory.getDictLabel());
                merged.setDictValue((!readonly && overrideRecord.getDictValue() != null) ? overrideRecord.getDictValue() : publicCategory.getDictValue());
                merged.setColor((!readonly && overrideRecord.getColor() != null) ? overrideRecord.getColor() : publicCategory.getColor());
                merged.setIcon((!readonly && overrideRecord.getIcon() != null) ? overrideRecord.getIcon() : publicCategory.getIcon());
                merged.setExtData((!readonly && overrideRecord.getExtData() != null) ? overrideRecord.getExtData() : publicCategory.getExtData());
                merged.setDictSort((!readonly && overrideRecord.getDictSort() != null) ? overrideRecord.getDictSort() : publicCategory.getDictSort());
                // 保留真实状态：覆盖记录的 status 优先；管理页需要看到停用项
                merged.setStatus(overrideRecord.getStatus() != null ? overrideRecord.getStatus() : publicCategory.getStatus());
                merged.setIsReadonly(publicCategory.getIsReadonly());
                result.add(merged);
            } else {
                // 没有覆盖记录
                if (!includeDisabled && "1".equals(publicCategory.getStatus())) {
                    continue;
                }
                // 原样展示公共分类
                result.add(publicCategory);
            }
        }

        // 4. 添加用户纯原创的私有分类
        List<UserDictDataEntity> privateCategories = userRecords.stream()
                .filter(record -> record.getTemplateId() == null
                        && (includeDisabled || "0".equals(record.getStatus())))
                .collect(Collectors.toList());
        result.addAll(privateCategories);

        // 5. 按 sort 升序排列
        result.sort(Comparator.comparing(UserDictDataEntity::getDictSort, Comparator.nullsLast(Integer::compareTo)));

        return result;
    }

    @Override
    public void createDictData(UserDictDataEntity entity, Long userId) {
        if (entity.getUserId() != null && entity.getUserId() == 0L) {
            throw new RuntimeException("普通用户不能创建公共分类");
        }
        
        entity.setId(null);
        entity.setUserId(userId);
        entity.setTemplateId(null);
        entity.setIsDeleted(0);
        entity.fillCreateCommonField(userId);
        
        this.save(entity);
    }

    @Override
    public void updateDictData(Long id, UserDictDataEntity updates, Long userId) {
        UserDictDataEntity target = this.getById(id);
        if (target == null) {
            throw new RuntimeException("字典数据不存在");
        }

        if (target.getUserId() == 0L) {
            // 目标是公共字典，生成或更新覆盖记录
            if ("Y".equals(target.getIsReadonly())) {
                // 如果是只读的，只允许用户修改状态，清空其他属性的修改
                UserDictDataEntity onlyStatusUpdate = new UserDictDataEntity();
                onlyStatusUpdate.setStatus(updates.getStatus());
                updates = onlyStatusUpdate;
            }

            UserDictDataEntity existingOverride = this.getOne(new LambdaQueryWrapper<UserDictDataEntity>()
                    .eq(UserDictDataEntity::getUserId, userId)
                    .eq(UserDictDataEntity::getTemplateId, id)
                    .eq(UserDictDataEntity::getIsDeleted, 0)
                    .last("LIMIT 1"));

            if (existingOverride != null) {
                // 更新已有的覆盖记录
                updates.setId(existingOverride.getId());
                updates.setUserId(userId);
                updates.setTemplateId(id);
                updates.fillUpdateCommonField(userId);
                this.updateById(updates);
            } else {
                // 插入新的覆盖记录
                updates.setId(null);
                updates.setUserId(userId);
                updates.setTemplateId(id);
                updates.fillCreateCommonField(userId);
                this.save(updates);
            }
        } else if (target.getUserId().equals(userId)) {
            // 目标是当前用户的记录（私有分类或已有的覆盖记录），直接更新
            updates.setId(id);
            updates.fillUpdateCommonField(userId);
            this.updateById(updates);
        } else {
            throw new RuntimeException("无权修改此字典数据");
        }
    }

    @Override
    public void deleteDictData(Long id, Long userId) {
        UserDictDataEntity target = this.getById(id);
        if (target == null) {
            throw new RuntimeException("字典数据不存在");
        }

        if (target.getUserId() == 0L) {
            // 目标是公共分类，生成隐藏记录
            UserDictDataEntity existingOverride = this.getOne(new LambdaQueryWrapper<UserDictDataEntity>()
                    .eq(UserDictDataEntity::getUserId, userId)
                    .eq(UserDictDataEntity::getTemplateId, id)
                    .eq(UserDictDataEntity::getIsDeleted, 0)
                    .last("LIMIT 1"));

            if (existingOverride != null) {
                // 更新已有覆盖记录的状态为停用 ("1")
                UserDictDataEntity update = new UserDictDataEntity();
                update.setId(existingOverride.getId());
                update.setStatus("1");
                update.fillUpdateCommonField(userId);
                this.updateById(update);
            } else {
                // 插入隐藏记录
                UserDictDataEntity hideRecord = new UserDictDataEntity();
                hideRecord.setUserId(userId);
                hideRecord.setTemplateId(id);
                hideRecord.setDictType(target.getDictType());
                hideRecord.setDictLabel(target.getDictLabel());
                hideRecord.setDictValue(target.getDictValue());
                hideRecord.setColor(target.getColor());
                hideRecord.setIcon(target.getIcon());
                hideRecord.setStatus("1"); // 停用（隐藏）
                hideRecord.fillCreateCommonField(userId);
                this.save(hideRecord);
            }
        } else if (target.getUserId().equals(userId)) {
            // 目标是当前用户的记录，直接物理删除或逻辑删除（已有 is_deleted 字段，使用 removeById 会触发逻辑删除）
            this.removeById(id);
        } else {
            throw new RuntimeException("无权删除此字典数据");
        }
    }
}
