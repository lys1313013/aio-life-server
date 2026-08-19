package top.aiolife.sso.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
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
    public ApiKeyEntity generateApiKey(Long userId, String remark, Integer expireDays) {
        ApiKeyEntity entity = new ApiKeyEntity();
        entity.setUserId(userId);
        entity.setRemark(remark);
        entity.setApiKey(generateUnusedApiKey());
        if (expireDays != null && expireDays > 0) {
            entity.setExpiredAt(LocalDateTime.now().plusDays(expireDays));
        }
        entity.setCreateUser(userId);
        entity.setUpdateUser(userId);
        this.save(entity);
        return entity;
    }

    /**
     * 生成当前有效记录中尚未使用的 API Key。
     *
     * <p>数据库允许逻辑删除后的 key 被再次使用；随机碰撞概率极低，有限重试用于防御性校验。</p>
     */
    private String generateUnusedApiKey() {
        for (int attempt = 0; attempt < MAX_GENERATE_ATTEMPTS; attempt++) {
            String candidate = "ak-" + IdUtil.fastSimpleUUID();
            long activeCount = this.count(new LambdaQueryWrapper<ApiKeyEntity>()
                    .eq(ApiKeyEntity::getApiKey, candidate));
            if (activeCount == 0) {
                return candidate;
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
