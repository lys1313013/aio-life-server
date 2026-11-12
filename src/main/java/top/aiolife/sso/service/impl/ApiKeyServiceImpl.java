package top.aiolife.sso.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import top.aiolife.sso.mapper.ApiKeyMapper;
import top.aiolife.sso.pojo.entity.ApiKeyEntity;
import top.aiolife.sso.service.IApiKeyService;

import java.time.LocalDateTime;
import java.util.List;

/**
 * API Key Service 实现类
 *
 * @author Lys
 * @date 2026/03/09
 */
@Service
@RequiredArgsConstructor
public class ApiKeyServiceImpl extends ServiceImpl<ApiKeyMapper, ApiKeyEntity> implements IApiKeyService {

    private static final int MAX_GENERATE_ATTEMPTS = 5;

    @Override
    @Transactional
    public ApiKeyEntity updateRemark(Long userId, Long id, String remark) {
        boolean updated = this.update(new LambdaUpdateWrapper<ApiKeyEntity>()
                .eq(ApiKeyEntity::getId, id)
                .eq(ApiKeyEntity::getUserId, userId)
                .set(ApiKeyEntity::getRemark, remark)
                .set(ApiKeyEntity::getUpdateUser, userId)
                .set(ApiKeyEntity::getUpdateTime, LocalDateTime.now()));
        if (!updated) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "API Key 不存在或已删除");
        }
        return this.getById(id);
    }

    @Override
    public ApiKeyEntity generateApiKey(Long userId, String remark, Integer expireDays) {
        for (int attempt = 0; attempt < MAX_GENERATE_ATTEMPTS; attempt++) {
            ApiKeyEntity entity = new ApiKeyEntity();
            entity.setUserId(userId);
            entity.setRemark(remark);
            entity.setApiKey("ak-" + IdUtil.fastSimpleUUID());
            if (expireDays != null && expireDays > 0) {
                entity.setExpiredAt(LocalDateTime.now().plusDays(expireDays));
            }
            entity.setCreateUser(userId);
            entity.setUpdateUser(userId);
            try {
                this.save(entity);
                return entity;
            } catch (DuplicateKeyException ignored) {
                // 唯一索引冲突时重新生成；每次 save 是独立自动提交语句，不处于未结束事务。
            }
        }
        throw new IllegalStateException("API Key 生成冲突，请稍后重试");
    }

    @Override
    public List<ApiKeyEntity> listByUserId(Long userId) {
        return this.list(new LambdaQueryWrapper<ApiKeyEntity>()
                .eq(ApiKeyEntity::getUserId, userId)
                .orderByDesc(ApiKeyEntity::getCreateTime));
    }

    @Override
    public ApiKeyEntity getByApiKey(String apiKey) {
        return this.getOne(new LambdaQueryWrapper<ApiKeyEntity>()
                .eq(ApiKeyEntity::getApiKey, apiKey));
    }
}
