package top.aiolife.record.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import top.aiolife.core.constant.ResponseCodeConst;
import top.aiolife.core.exception.ApiException;
import top.aiolife.core.query.QueryOrder;
import top.aiolife.record.mapper.IPasswordVaultMapper;
import top.aiolife.record.pojo.entity.PasswordVaultEntity;
import top.aiolife.record.service.IPasswordVaultService;

/**
 * 密码库Service实现
 *
 * @author Lys
 * @date 2026/04/28
 */
@Service
public class PasswordVaultServiceImpl extends ServiceImpl<IPasswordVaultMapper, PasswordVaultEntity>
        implements IPasswordVaultService {

    @Override
    public List<PasswordVaultEntity> listForUser(long userId) {
        LambdaQueryWrapper<PasswordVaultEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PasswordVaultEntity::getUserId, userId);
        QueryOrder.descNullsLast(wrapper, PasswordVaultEntity::getUpdateTime, PasswordVaultEntity::getId);
        return baseMapper.selectList(wrapper);
    }

    @Override
    public PasswordVaultEntity getOwned(long userId, long id) {
        return baseMapper.selectOne(new LambdaQueryWrapper<PasswordVaultEntity>()
                .eq(PasswordVaultEntity::getId, id)
                .eq(PasswordVaultEntity::getUserId, userId));
    }

    @Override
    public void createForUser(long userId, PasswordVaultEntity entity) {
        entity.setUserId(userId);
        entity.setCreateUser(userId);
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        if (entity.getFavorite() == null) {
            entity.setFavorite(false);
        }
        if (entity.getCategory() == null || entity.getCategory().isEmpty()) {
            entity.setCategory("其他");
        }
        baseMapper.insert(entity);
    }

    @Override
    public void updateForUser(long userId, long id, PasswordVaultEntity entity) {
        PasswordVaultEntity existing = baseMapper.selectById(id);
        if (existing == null || !Long.valueOf(userId).equals(existing.getUserId())) {
            throw new ApiException(ResponseCodeConst.COMMON_FAIL, "记录不存在或无权限操作");
        }
        entity.setId(id);
        // 防止请求方篡改记录归属
        entity.setUserId(null);
        entity.setCreateUser(null);
        entity.setUpdateUser(userId);
        entity.setUpdateTime(LocalDateTime.now());
        baseMapper.updateById(entity);
    }

    @Override
    public void deleteForUser(long userId, long id) {
        baseMapper.delete(new LambdaQueryWrapper<PasswordVaultEntity>()
                .eq(PasswordVaultEntity::getId, id)
                .eq(PasswordVaultEntity::getUserId, userId));
    }

    @Override
    public List<String> listCategories(long userId) {
        LambdaQueryWrapper<PasswordVaultEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PasswordVaultEntity::getUserId, userId);
        wrapper.select(PasswordVaultEntity::getCategory);
        wrapper.groupBy(PasswordVaultEntity::getCategory);
        List<PasswordVaultEntity> list = baseMapper.selectList(wrapper);

        Set<String> categorySet = new LinkedHashSet<>(Arrays.asList(
                "工作", "生活", "学习", "金融", "社交", "游戏", "其他"));
        for (PasswordVaultEntity entity : list) {
            if (entity.getCategory() != null && !entity.getCategory().isEmpty()) {
                categorySet.add(entity.getCategory());
            }
        }
        return new ArrayList<>(categorySet);
    }
}
