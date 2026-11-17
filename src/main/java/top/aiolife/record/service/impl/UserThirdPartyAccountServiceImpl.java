package top.aiolife.record.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import top.aiolife.core.redis.RedisUtil;
import top.aiolife.record.client.DoubanAccountClient;
import top.aiolife.record.mapper.UserThirdPartyAccountMapper;
import top.aiolife.record.notification.NotificationCryptoService;
import top.aiolife.record.pojo.entity.UserThirdPartyAccountEntity;
import top.aiolife.record.pojo.vo.DoubanAccountVO;
import top.aiolife.record.service.IUserThirdPartyAccountService;
import top.aiolife.record.service.IWereadService;

import java.util.List;

/**
 * 用户绑定服务实现类
 */
@Service
@RequiredArgsConstructor
public class UserThirdPartyAccountServiceImpl extends ServiceImpl<UserThirdPartyAccountMapper, UserThirdPartyAccountEntity> implements IUserThirdPartyAccountService {

    private static final String TOKEN_PURPOSE = "user-bind";

    private final IWereadService wereadService;

    private final RedisUtil redisUtil;

    private final DoubanAccountClient doubanAccountClient;

    private final NotificationCryptoService crypto;

    @Override
    public List<UserThirdPartyAccountEntity> getBindsByUserId(Long userId) {
        List<UserThirdPartyAccountEntity> list = this.list(new LambdaQueryWrapper<UserThirdPartyAccountEntity>()
                .eq(UserThirdPartyAccountEntity::getUserId, userId));
        list.forEach(this::decryptToken);
        return list;
    }

    @Override
    public UserThirdPartyAccountEntity getBindByUserIdAndPlatform(Long userId, String platform) {
        return decryptToken(this.getOne(new LambdaQueryWrapper<UserThirdPartyAccountEntity>()
                .eq(UserThirdPartyAccountEntity::getUserId, userId)
                .eq(UserThirdPartyAccountEntity::getPlatform, platform)));
    }

    @Override
    public List<UserThirdPartyAccountEntity> listForUser(long userId, Boolean includeToken) {
        List<UserThirdPartyAccountEntity> list = getBindsByUserId(userId);
        list.forEach(item -> {
            // 微信读书凭证始终隐藏，其他平台保留原 includeToken 行为。
            if ("weread".equalsIgnoreCase(item.getPlatform()) || includeToken == null || !includeToken) {
                item.setAccessToken(null);
            }
        });
        return list;
    }

    @Override
    public DoubanAccountVO verifyDoubanAccount(String accountId) {
        return doubanAccountClient.verify(accountId);
    }

    @Override
    public boolean createForUser(long userId, UserThirdPartyAccountEntity entity) {
        entity.setUserId(userId);

        if ("weread".equalsIgnoreCase(entity.getPlatform())
                && (entity.getAccessToken() == null || entity.getAccessToken().isBlank())) {
            throw new IllegalArgumentException("微信读书 API Key 不能为空");
        }

        // 检查是否存在同平台绑定
        UserThirdPartyAccountEntity exist = getBindByUserIdAndPlatform(userId, entity.getPlatform());
        if (exist != null) {
            throw new IllegalArgumentException("该平台账号已绑定，请勿重复添加");
        }

        if ("weread".equalsIgnoreCase(entity.getPlatform())) {
            wereadService.connect(userId, entity.getAccessToken().trim());
            return true;
        }

        entity.fillCreateCommonField(userId);
        if (StringUtils.hasText(entity.getAccessToken())) {
            entity.setAccessTokenCiphertext(encryptToken(userId, entity.getAccessToken()));
        }
        boolean success;
        try {
            success = save(entity);
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException("该平台账号已绑定，请勿重复添加", e);
        }
        evictGithubVisibleCache(userId, entity.getPlatform());
        return success;
    }

    @Override
    public boolean updateForUser(long userId, UserThirdPartyAccountEntity entity) {
        entity.setUserId(userId);

        // 检查记录是否存在
        if (entity.getId() == null) {
            throw new IllegalArgumentException("ID不能为空");
        }
        UserThirdPartyAccountEntity exist = getById(entity.getId());
        if (exist == null || !exist.getUserId().equals(userId)) {
            throw new IllegalArgumentException("记录不存在或无权操作");
        }

        if ("weread".equalsIgnoreCase(exist.getPlatform()) || "weread".equalsIgnoreCase(entity.getPlatform())) {
            if (entity.getPlatform() != null && !exist.getPlatform().equalsIgnoreCase(entity.getPlatform())) {
                throw new IllegalArgumentException("请先解绑，再添加其他平台账号");
            }
            // 留空保留原 Key；不接受客户端回写服务端同步元数据。
            if (entity.getAccessToken() != null && !entity.getAccessToken().isBlank()) {
                wereadService.connect(userId, entity.getAccessToken().trim());
            }
            return true;
        }

        entity.fillUpdateCommonField(userId);

        // 留空表示保留原令牌；只加密本次提交的新值。
        if (StringUtils.hasText(entity.getAccessToken())) {
            entity.setAccessTokenCiphertext(encryptToken(userId, entity.getAccessToken()));
        } else {
            entity.setAccessTokenCiphertext(null);
        }

        boolean success;
        try {
            success = updateById(entity);
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException("该平台账号已绑定，请勿重复添加", e);
        }
        evictGithubVisibleCache(userId, exist.getPlatform());
        return success;
    }

    @Override
    public boolean deleteForUser(long userId, long id) {
        UserThirdPartyAccountEntity entity = getById(id);
        if (entity == null || !entity.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权操作或记录不存在");
        }
        if ("weread".equalsIgnoreCase(entity.getPlatform())) {
            wereadService.disconnect(userId);
            return true;
        }
        boolean success = removeById(id);
        evictGithubVisibleCache(userId, entity.getPlatform());
        return success;
    }

    /**
     * github 绑定变更后失效 Redis 中缓存的「是否展示 GitHub 卡片」决策，使首页判断立即生效
     */
    private void evictGithubVisibleCache(long userId, String platform) {
        if ("github".equals(platform)) {
            redisUtil.delete("github:visible:" + userId);
        }
    }

    private UserThirdPartyAccountEntity decryptToken(UserThirdPartyAccountEntity entity) {
        if (entity != null) {
            String ciphertext = entity.getAccessTokenCiphertext();
            entity.setAccessToken(StringUtils.hasText(ciphertext)
                    ? crypto.decrypt(TOKEN_PURPOSE, ciphertext, aad(entity.getUserId())) : null);
        }
        return entity;
    }

    private String encryptToken(long userId, String token) {
        return crypto.encrypt(TOKEN_PURPOSE, token.trim(), aad(userId));
    }

    private static String aad(Long userId) {
        return "user_bind:" + userId;
    }
}
