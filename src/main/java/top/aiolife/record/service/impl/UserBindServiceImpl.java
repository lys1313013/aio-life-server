package top.aiolife.record.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import top.aiolife.core.redis.RedisUtil;
import top.aiolife.record.client.DoubanAccountClient;
import top.aiolife.record.mapper.UserBindMapper;
import top.aiolife.record.pojo.entity.UserBindEntity;
import top.aiolife.record.pojo.vo.DoubanAccountVO;
import top.aiolife.record.service.IUserBindService;
import top.aiolife.record.service.IWereadService;

import java.util.List;

/**
 * 用户绑定服务实现类
 */
@Service
@RequiredArgsConstructor
public class UserBindServiceImpl extends ServiceImpl<UserBindMapper, UserBindEntity> implements IUserBindService {

    private final IWereadService wereadService;

    private final RedisUtil redisUtil;

    private final DoubanAccountClient doubanAccountClient;

    @Override
    public List<UserBindEntity> getBindsByUserId(Long userId) {
        return this.list(new LambdaQueryWrapper<UserBindEntity>()
                .eq(UserBindEntity::getUserId, userId));
    }

    @Override
    public UserBindEntity getBindByUserIdAndPlatform(Long userId, String platform) {
        return this.getOne(new LambdaQueryWrapper<UserBindEntity>()
                .eq(UserBindEntity::getUserId, userId)
                .eq(UserBindEntity::getPlatform, platform));
    }

    @Override
    public List<UserBindEntity> listForUser(long userId, Boolean includeToken) {
        List<UserBindEntity> list = getBindsByUserId(userId);
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
    public boolean createForUser(long userId, UserBindEntity entity) {
        entity.setUserId(userId);

        if ("weread".equalsIgnoreCase(entity.getPlatform())
                && (entity.getAccessToken() == null || entity.getAccessToken().isBlank())) {
            throw new IllegalArgumentException("微信读书 API Key 不能为空");
        }

        // 检查是否存在同平台绑定
        UserBindEntity exist = getBindByUserIdAndPlatform(userId, entity.getPlatform());
        if (exist != null) {
            throw new IllegalArgumentException("该平台账号已绑定，请勿重复添加");
        }

        if ("weread".equalsIgnoreCase(entity.getPlatform())) {
            wereadService.connect(userId, entity.getAccessToken().trim());
            return true;
        }

        entity.fillCreateCommonField(userId);
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
    public boolean updateForUser(long userId, UserBindEntity entity) {
        entity.setUserId(userId);

        // 检查记录是否存在
        if (entity.getId() == null) {
            throw new IllegalArgumentException("ID不能为空");
        }
        UserBindEntity exist = getById(entity.getId());
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

        // 如果Access Token为空，则保留原值（不更新）
        if (entity.getAccessToken() == null || entity.getAccessToken().isEmpty()) {
            entity.setAccessToken(exist.getAccessToken());
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
        UserBindEntity entity = getById(id);
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
}
