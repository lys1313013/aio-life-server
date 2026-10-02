package top.aiolife.sso.service;

import cn.dev33.satoken.exception.NotLoginException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import top.aiolife.sso.mapper.UserMapper;
import top.aiolife.sso.pojo.entity.UserEntity;

/** 认证时直接检查账号，避免历史遗留凭据或用户资料缓存绕过逻辑删除。 */
@Component
@RequiredArgsConstructor
public class AccountStatusGuard {
    private final UserMapper userMapper;

    public boolean isActive(Long userId) {
        return userId != null && userMapper.selectCount(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getId, userId)) > 0;
    }

    public void requireActive(Long userId) {
        if (!isActive(userId)) {
            throw new NotLoginException("账号不存在或已删除", "login", NotLoginException.INVALID_TOKEN);
        }
    }
}
