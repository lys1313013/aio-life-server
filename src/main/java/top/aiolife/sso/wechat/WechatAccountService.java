package top.aiolife.sso.wechat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import top.aiolife.sso.mapper.UserMapper;
import top.aiolife.sso.pojo.entity.UserEntity;
import top.aiolife.sso.util.PasswordUtil;


/** 只在短数据库事务内变更认证字段，微信网络调用由上层提前完成。 */
@Service
@RequiredArgsConstructor
public class WechatAccountService {
    private final UserMapper users;

    public UserEntity findByOpenid(String openid) {
        return users.selectOne(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getWechatOpenid, openid));
    }

    @Transactional
    public Registration register(WechatTicketStore.Ticket ticket) {
        UserEntity identityOwner = findByOpenid(ticket.openid());
        if (identityOwner != null) {
            return new Registration(identityOwner, false);
        }
        UserEntity user = new UserEntity();
        user.setId(IdWorker.getId());
        user.setUsername("u_" + user.getId());
        user.setNickname("生活记录者");
        user.setRole("user");
        user.setIsDeleted(0);
        user.setWechatOpenid(ticket.openid());
        user.setWechatUnionid(ticket.unionid());
        user.setCreateUser(user.getId());
        user.setUpdateUser(user.getId());
        users.insert(user);
        return new Registration(user, true);
    }

    @Transactional
    @CacheEvict(value = {"userInfo", "userBasicInfo"}, key = "#userId")
    public UserEntity bind(long userId, String password, WechatTicketStore.Ticket ticket) {
        UserEntity user = users.selectForAuthUpdate(userId);
        if (user == null || !StringUtils.hasText(user.getPassword()) || !StringUtils.hasText(password)
                || !user.getPassword().equals(PasswordUtil.encryptPassword(password, user.getPasswordSalt()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "原账号密码验证失败");
        }
        UserEntity owner = findByOpenid(ticket.openid());
        if (owner != null && owner.getId() != userId) throw conflict();
        if (StringUtils.hasText(user.getWechatOpenid()) && !user.getWechatOpenid().equals(ticket.openid())) throw conflict();
        LambdaUpdateWrapper<UserEntity> update = new LambdaUpdateWrapper<UserEntity>()
                .eq(UserEntity::getId, userId).set(UserEntity::getWechatOpenid, ticket.openid())
                .set(UserEntity::getWechatUnionid, ticket.unionid()).set(UserEntity::getUpdateUser, userId);
        if (users.update(null, update) != 1) throw conflict();
        return users.selectById(userId);
    }

    @Transactional
    @CacheEvict(value = "userInfo", key = "#userId")
    public void initializePassword(long userId, String openid, String password) {
        UserEntity user = users.selectForAuthUpdate(userId);
        if (user == null || !openid.equals(user.getWechatOpenid())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "微信身份与当前账号不一致");
        }
        if (StringUtils.hasText(user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "已设置密码，请使用修改密码功能");
        }
        String salt = PasswordUtil.getSalt();
        users.update(null, new LambdaUpdateWrapper<UserEntity>().eq(UserEntity::getId, userId)
                .set(UserEntity::getPasswordSalt, salt)
                .set(UserEntity::getPassword, PasswordUtil.encryptPassword(password, salt)));
    }

    private ResponseStatusException conflict() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "微信与已有账号绑定冲突，请使用原账号登录");
    }

    public record Registration(UserEntity user, boolean newUser) {}
}
