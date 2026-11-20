package top.aiolife.sso.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import top.aiolife.sso.service.SecondaryLockMenuResolver;
import top.aiolife.core.mybatis.IlikeQuery;
import top.aiolife.core.query.CommonQuery;
import top.aiolife.core.resq.PageResp;
import top.aiolife.record.service.IMailService;
import top.aiolife.core.redis.RedisUtil;
import top.aiolife.sso.convertor.UserConvertor;
import top.aiolife.sso.interceptor.SecondaryLockInterceptor;
import top.aiolife.sso.util.SecondaryPasswordPolicy;
import top.aiolife.sso.util.SecondaryLockSession;
import top.aiolife.sso.mapper.ApiKeyMapper;
import top.aiolife.sso.mapper.LoginLogMapper;
import top.aiolife.sso.mapper.UserMapper;
import top.aiolife.sso.mapper.UserSecondaryLockMenuMapper;
import top.aiolife.sso.pojo.entity.ApiKeyEntity;
import top.aiolife.sso.pojo.entity.LoginLogEntity;
import top.aiolife.sso.pojo.entity.UserEntity;
import top.aiolife.sso.pojo.entity.UserSecondaryLockMenuEntity;
import top.aiolife.sso.pojo.query.UserQuery;
import top.aiolife.sso.pojo.req.ChangePasswordReq;
import top.aiolife.sso.pojo.req.LoginReq;
import top.aiolife.sso.pojo.req.RegisterReq;
import top.aiolife.sso.pojo.req.ResetPasswordReq;
import top.aiolife.sso.pojo.req.UpdateUserReq;
import top.aiolife.sso.pojo.vo.UserBasicInfoVO;
import top.aiolife.sso.pojo.vo.UserInfoVO;
import top.aiolife.sso.pojo.vo.UserLoginVO;
import top.aiolife.sso.pojo.vo.UserVO;
import top.aiolife.sso.service.IUserService;
import top.aiolife.sso.service.AuthAttemptStore;
import top.aiolife.sso.service.AuthAttemptStore.EmailPurpose;
import top.aiolife.sso.service.LoginSessionService;
import top.aiolife.sso.service.LegalPolicyService;
import top.aiolife.sso.service.UserAvatarFileService;
import top.aiolife.sso.util.PasswordUtil;

/**
 * 用户服务实现类
 *
 * @author Lys
 * @date 2025/12/06 23:58
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements IUserService {

    private static final String LAST_ACTIVE_KEY_PREFIX = "user:last_active:";

    private final UserMapper userMapper;
    private final ApiKeyMapper apiKeyMapper;
    private final LoginSessionService loginSessionService;
    private final LoginLogMapper loginLogMapper;
    private final IMailService mailService;
    private final RedisUtil redisUtil;
    private final UserSecondaryLockMenuMapper lockMenuMapper;
    private final SecondaryLockMenuResolver secondaryLockMenuResolver;
    private final UserAvatarFileService avatarFiles;
    private final AuthAttemptStore authAttempts;

    @Override
    public UserLoginVO login(LoginReq loginReq, String ip) {
        var attempt = authAttempts.reserveLogin(loginReq.getUsername(), ip);
        LambdaQueryWrapper<UserEntity> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        IlikeQuery.lowerEquals(lambdaQueryWrapper, UserEntity::getUsername, loginReq.getUsername());

        UserEntity userEntity = userMapper.selectOne(lambdaQueryWrapper);

        if (userEntity == null || !PasswordUtil.matches(loginReq.getPassword(), userEntity.getPassword())) {
            // 失败登录也不记录输入密码，避免把真实旧密码或输错的密码写入日志。
            LoginLogEntity loginLogEntity = new LoginLogEntity();
            loginLogEntity.setUsername(loginReq.getUsername());
            loginLogEntity.setClientIp(ip);
            loginLogMapper.insert(loginLogEntity);
            throw new RuntimeException("用户名或密码错误");
        }

        authAttempts.releaseSuccessfulLogin(attempt);
        return loginSessionService.complete(userEntity, ip, true);
    }

    @Override
    @Cacheable(value = "userInfo", key = "#userId")
    public UserInfoVO getUserInfo(long userId) {
        UserEntity userEntity = userMapper.selectById(userId);
        if (userEntity == null) {
            return null;
        }
        UserInfoVO userInfoVO = new UserInfoVO();
        userInfoVO.setId(userEntity.getId());
        userInfoVO.setRealName(userEntity.getNickname());
        userInfoVO.setUsername(userEntity.getNickname());
        userInfoVO.setNickname(userEntity.getNickname());
        userInfoVO.setAccountUsername(userEntity.getUsername());
        userInfoVO.setHasPassword(StringUtils.hasText(userEntity.getPassword()));
        userInfoVO.setWechatBound(StringUtils.hasText(userEntity.getWechatOpenid()));
        if (StringUtils.hasText(userEntity.getPhone())) {
            String phone = userEntity.getPhone();
            userInfoVO.setPhoneMasked("+" + userEntity.getPhoneCountryCode() + " ****"
                    + phone.substring(Math.max(0, phone.length() - 4)));
        }
        var avatar = avatarFiles.current(userEntity.getId());
        userInfoVO.setAvatarFileId(avatar.fileId());
        userInfoVO.setAvatarUrl(avatar.url());
        userInfoVO.setEmail(userEntity.getEmail());
        userInfoVO.setIntroduction(userEntity.getIntroduction());
        userInfoVO.setRoles(StringUtils.hasText(userEntity.getRole()) ? Arrays.asList(userEntity.getRole().split(",")) : Collections.singletonList("user"));
        return userInfoVO;
    }

    @Override
    @Cacheable(value = "userBasicInfo", key = "#userId")
    public UserBasicInfoVO getUserBasicInfo(long userId) {
        UserEntity userEntity = userMapper.selectById(userId);
        if (userEntity == null) {
            return null;
        }
        UserBasicInfoVO vo = new UserBasicInfoVO();
        vo.setId(userEntity.getId());
        vo.setNickname(userEntity.getNickname());
        var avatar = avatarFiles.current(userEntity.getId());
        vo.setAvatarFileId(avatar.fileId());
        vo.setAvatarUrl(avatar.url());
        return vo;
    }

    @Override
    public String getRole(long userId) {
        UserEntity user = userMapper.selectById(userId);
        return user == null ? null : user.getRole();
    }

    @Override
    public List<String> listRoles(long userId) {
        String role = getRole(userId);
        if (!StringUtils.hasText(role)) {
            return List.of("user");
        }
        return List.of(role.split(",")).stream()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    @Override
    @CacheEvict(value = {"userInfo", "userBasicInfo"}, key = "#userId")
    @Transactional(rollbackFor = Exception.class)
    public void updateProfile(long userId, UpdateUserReq req) {
        UserEntity userEntity = new UserEntity();
        userEntity.setId(userId);
        userEntity.setNickname(req.getNickname());
        userEntity.setIntroduction(req.getIntroduction());
        userEntity.setUpdateUser(userId);
        updateUser(userEntity, req.getAvatarFileId(), req.isAvatarFileIdSpecified());
    }

    @Override
    public List<UserEntity> listAdminUsers() {
        return userMapper.selectList(new LambdaQueryWrapper<UserEntity>()
                .like(UserEntity::getRole, "admin")
                .eq(UserEntity::getIsDeleted, 0)
                .orderByAsc(UserEntity::getId));
    }

    @Override
    public List<String> listRoleOptions() {
        List<UserEntity> users = userMapper.selectList(new LambdaQueryWrapper<UserEntity>()
                .select(UserEntity::getRole)
                .eq(UserEntity::getIsDeleted, 0));
        Set<String> roles = new TreeSet<>();
        roles.add("admin");
        roles.add("user");
        for (UserEntity user : users) {
            if (user == null || !StringUtils.hasText(user.getRole())) continue;
            for (String role : user.getRole().split(",")) {
                String normalized = role.trim();
                if (!normalized.isEmpty()) roles.add(normalized);
            }
        }
        return List.copyOf(roles);
    }

    @Override
    @CacheEvict(value = {"userInfo", "userBasicInfo"}, key = "#userEntity.id")
    @Transactional(rollbackFor = Exception.class)
    public void updateUser(UserEntity userEntity, String avatarFileId, boolean avatarFileIdSpecified) {
        // 禁止通过此接口修改密码
        userEntity.setPassword(null);
        clearExternalCredentials(userEntity);
        userEntity.setUpdateTime(LocalDateTime.now());
        try {
            if (userEntity.getNickname() != null || userEntity.getIntroduction() != null
                    || userEntity.getUsername() != null || userEntity.getEmail() != null || userEntity.getRole() != null) {
                userMapper.updateById(userEntity);
            }
        } catch (DuplicateKeyException e) {
            throw new IllegalStateException("用户名或邮箱已被占用");
        }
        if (avatarFileIdSpecified) avatarFiles.replace(userEntity.getId(), avatarFileId);
    }

    @Override
    public void changePassword(long userId, ChangePasswordReq changePasswordReq) {
        UserEntity userEntity = userMapper.selectById(userId);
        if (userEntity == null) {
            throw new RuntimeException("用户不存在");
        }
        if (!StringUtils.hasText(userEntity.getPassword())) {
            throw new RuntimeException("尚未设置密码，请通过微信验证后设置登录密码");
        }
        if (!PasswordUtil.matches(changePasswordReq.getOldPassword(), userEntity.getPassword())) {
            throw new RuntimeException("旧密码错误");
        }
        userEntity.setPassword(PasswordUtil.hashPassword(changePasswordReq.getNewPassword()));
        userEntity.setUpdateTime(LocalDateTime.now());
        userEntity.setUpdateUser(userId);
        userMapper.updateById(userEntity);
    }

    @Override
    public PageResp<UserVO> getUserList(CommonQuery<UserQuery> query) {
        Page<UserEntity> page = new Page<>(query.getPage(), query.getPageSize());
        LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<>();
        String keyword = query.getCondition() == null ? null : query.getCondition().getKeyword();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(where -> IlikeQuery.anyOf(where, keyword.trim(),
                    UserEntity::getUsername, UserEntity::getNickname, UserEntity::getEmail));
        }
        wrapper.orderByDesc(UserEntity::getCreateTime);
        userMapper.selectPage(page, wrapper);

        Map<Long, LocalDateTime> redisLastActiveMap = buildRedisLastActiveMap(page.getRecords());
        List<UserVO> voList = page.getRecords().stream().map(user -> {
            UserVO vo = UserConvertor.INSTANCE.entity2VO(user);
            vo.setIsOnline(StpUtil.isLogin(user.getId()));
            vo.setLastActiveTime(maxTime(vo.getLastActiveTime(), redisLastActiveMap.get(user.getId())));
            return vo;
        }).collect(Collectors.toList());

        return new PageResp<>(voList, page.getTotal());
    }

    private Map<Long, LocalDateTime> buildRedisLastActiveMap(List<UserEntity> users) {
        if (users == null || users.isEmpty()) {
            return Map.of();
        }
        List<Long> userIds = users.stream()
                .map(UserEntity::getId)
                .filter(Objects::nonNull)
                .toList();
        if (userIds.isEmpty()) {
            return Map.of();
        }

        List<String> keys = userIds.stream()
                .map(id -> LAST_ACTIVE_KEY_PREFIX + id)
                .toList();
        List<String> values = redisUtil.multiGet(keys);
        if (values == null || values.isEmpty()) {
            return Map.of();
        }

        ZoneId zoneId = ZoneId.systemDefault();
        return IntStream.range(0, Math.min(userIds.size(), values.size()))
                .boxed()
                .filter(i -> toLocalDateTime(values.get(i), zoneId) != null)
                .map(i -> Map.entry(userIds.get(i), toLocalDateTime(values.get(i), zoneId)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private LocalDateTime toLocalDateTime(String epochMillisStr, ZoneId zoneId) {
        if (!StringUtils.hasText(epochMillisStr)) {
            return null;
        }
        try {
            long epochMillis = Long.parseLong(epochMillisStr);
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), zoneId);
        } catch (Exception ignore) {
            return null;
        }
    }

    private LocalDateTime maxTime(LocalDateTime a, LocalDateTime b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.isAfter(b) ? a : b;
    }

    /** 普通资料及管理端表单不能绕过微信认证写入登录凭证。 */
    private void clearExternalCredentials(UserEntity user) {
        user.setPhone(null);
        user.setPhoneCountryCode(null);
        user.setPhoneVerifiedAt(null);
        user.setWechatOpenid(null);
        user.setWechatUnionid(null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addUser(UserEntity userEntity, String avatarFileId) {
        clearExternalCredentials(userEntity);
        if (!StringUtils.hasText(userEntity.getPassword())) {
            userEntity.setPassword("123456");
        }
        userEntity.setPassword(PasswordUtil.hashPassword(userEntity.getPassword()));
        try {
            userMapper.insert(userEntity);
        } catch (DuplicateKeyException e) {
            throw new IllegalStateException("用户名或邮箱已被占用，请更换后重试");
        }
        if (avatarFileId != null) avatarFiles.replace(userEntity.getId(), avatarFileId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = {"userInfo", "userBasicInfo"}, key = "#id")
    public void deleteUser(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("用户 ID 不能为空");
        }
        userMapper.deleteById(id);
        apiKeyMapper.delete(new LambdaQueryWrapper<ApiKeyEntity>().eq(ApiKeyEntity::getUserId, id));
        // 指定账号撤销所有设备的会话；Redis 失败时抛出异常，回滚数据库删除。
        StpUtil.logout(id);
    }

    @Override
    public void sendRegisterCode(String email, String ip) {
        requireEmail(email);
        // 校验邮箱是否已注册
        LambdaQueryWrapper<UserEntity> emailWrapper = new LambdaQueryWrapper<>();
        IlikeQuery.lowerEquals(emailWrapper, UserEntity::getEmail, email);
        if (userMapper.selectCount(emailWrapper) > 0) {
            throw new RuntimeException("邮箱已被注册");
        }

        sendEmailCode(EmailPurpose.REGISTER, email, ip, "注册验证码", "register");
    }

    @Override
    public void sendResetPasswordCode(String email, String ip) {
        requireEmail(email);
        UserEntity user = findByEmail(email);
        if (user == null) {
            throw new RuntimeException("邮箱未注册");
        }

        sendEmailCode(EmailPurpose.RESET_PASSWORD, user.getEmail(), ip, "重置密码验证码", "reset_pwd");
    }

    private UserEntity findByEmail(String email) {
        LambdaQueryWrapper<UserEntity> emailWrapper = new LambdaQueryWrapper<>();
        IlikeQuery.lowerEquals(emailWrapper, UserEntity::getEmail, email);
        return userMapper.selectOne(emailWrapper);
    }

    private void sendEmailCode(EmailPurpose purpose, String email, String ip, String title, String bizType) {
        String code = authAttempts.issueEmailCode(purpose, email, ip);
        mailService.sendSimpleEmail(email, title,
                "您的验证码是：" + code + "，有效期" + AuthAttemptStore.CODE_MINUTES + "分钟。", bizType, ip);
    }

    /** 发送验证码前校验邮箱必填且格式有效。 */
    private void requireEmail(String email) {
        if (!StringUtils.hasText(email)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "邮箱不能为空");
        }
        if (!email.contains("@")) {
            throw new RuntimeException("邮箱格式不正确");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(ResetPasswordReq resetPasswordReq) {
        PasswordUtil.validatePassword(resetPasswordReq.getPassword());
        UserEntity userEntity = findByEmail(resetPasswordReq.getEmail());
        if (userEntity == null) {
            throw new IllegalArgumentException("验证码错误或已过期");
        }
        authAttempts.consumeEmailCode(EmailPurpose.RESET_PASSWORD, userEntity.getEmail(), resetPasswordReq.getCode());

        userEntity.setPassword(PasswordUtil.hashPassword(resetPasswordReq.getPassword()));
        userEntity.setUpdateTime(LocalDateTime.now());
        userEntity.setUpdateUser(userEntity.getId());
        if (userMapper.updateById(userEntity) != 1) {
            throw new IllegalStateException("密码重置失败，请重试");
        }
        // 找回密码后，所有旧登录凭据必须重新登录；失败时不提交新密码。
        StpUtil.logout(userEntity.getId());
    }

    @Override
    public void register(RegisterReq registerReq) {
        PasswordUtil.validatePassword(registerReq.getPassword());
        LegalPolicyService.requireAcceptance(registerReq.getAgreedPolicy(), registerReq.getPolicyVersion());
        authAttempts.consumeEmailCode(EmailPurpose.REGISTER, registerReq.getEmail(), registerReq.getCode());
        // 校验用户名是否已存在
        LambdaQueryWrapper<UserEntity> usernameWrapper = new LambdaQueryWrapper<>();
        IlikeQuery.lowerEquals(usernameWrapper, UserEntity::getUsername, registerReq.getUsername());
        if (userMapper.selectCount(usernameWrapper) > 0) {
            throw new RuntimeException("用户名已存在");
        }

        // 校验邮箱是否已存在（双重检查）
        LambdaQueryWrapper<UserEntity> emailWrapper = new LambdaQueryWrapper<>();
        IlikeQuery.lowerEquals(emailWrapper, UserEntity::getEmail, registerReq.getEmail());
        if (userMapper.selectCount(emailWrapper) > 0) {
            throw new RuntimeException("邮箱已被注册");
        }

        UserEntity userEntity = new UserEntity();
        userEntity.setUsername(registerReq.getUsername());
        userEntity.setNickname(registerReq.getUsername()); // 默认昵称同用户名
        userEntity.setEmail(registerReq.getEmail());
        userEntity.setPassword(registerReq.getPassword());
        userEntity.setRole("user"); // 默认普通用户

        userEntity.setPassword(PasswordUtil.hashPassword(userEntity.getPassword()));

        LegalPolicyService.recordAcceptance(userEntity);
        try {
            userMapper.insert(userEntity);
        } catch (DuplicateKeyException e) {
            throw new IllegalStateException("用户名或邮箱已被注册，请更换后重试");
        }
    }

    @Override
    public boolean hasSecondaryPassword(long userId) {
        UserEntity user = userMapper.selectById(userId);
        return user != null && StringUtils.hasText(user.getSecondaryPassword());
    }

    @Override
    public void setSecondaryPassword(long userId, String password, String oldPassword) {
        SecondaryPasswordPolicy.validate(password);
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        // 如果已有二级密码，需要验证旧密码
        if (StringUtils.hasText(user.getSecondaryPassword())) {
            if (!StringUtils.hasText(oldPassword)) {
                throw new RuntimeException("请提供旧二级密码");
            }
            // 修改入口沿用同一数字密码规则及用户级失败次数限制。
            verifySecondaryPasswordOrThrow(user, oldPassword);
        }

        user.setSecondaryPassword(PasswordUtil.hashPassword(password));
        user.setUpdateTime(LocalDateTime.now());
        user.setUpdateUser(user.getId());
        userMapper.updateById(user);
    }

    @Override
    public void verifySecondaryPassword(long userId, String password, String menuPath) {
        String sessionHash = SecondaryLockSession.currentHash();
        if (sessionHash == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "请使用登录会话解锁菜单");
        }
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        if (!StringUtils.hasText(user.getSecondaryPassword())) {
            throw new RuntimeException("未设置二级密码");
        }

        if (menuPath == null || menuPath.isBlank()) {
            throw new IllegalArgumentException("菜单路径不能为空");
        }
        String path = menuPath.split("\\?", 2)[0];
        if (path.startsWith("/api/")) path = path.substring(4);
        Set<String> paths = secondaryLockMenuResolver.findMatchedPaths(userId, path);
        verifySecondaryPasswordOrThrow(user, password);
        // 一次密码验证同时满足当前菜单及其父级锁，授权仅归当前登录会话。
        if (paths.isEmpty()) paths = Set.of(menuPath);
        for (String lockedPath : paths) {
            String unlockKey = SecondaryLockSession.unlockKey(userId, sessionHash, lockedPath);
            redisUtil.set(unlockKey, "1", SecondaryLockInterceptor.unlockTtlSeconds(), TimeUnit.SECONDS);
        }
    }

    /**
     * 校验二级密码是否正确，含失败次数限制；正确后清除失败计数。
     */
    private void verifySecondaryPasswordOrThrow(UserEntity user, String password) {
        SecondaryPasswordPolicy.validate(password);
        long userId = user.getId();
        String failKey = SecondaryLockInterceptor.failCountKey(userId);
        String failCountStr = redisUtil.get(failKey);
        int failCount = failCountStr == null ? 0 : Integer.parseInt(failCountStr);
        if (failCount >= SecondaryLockInterceptor.maxFailCount()) {
            throw new RuntimeException("尝试次数过多，请" + SecondaryLockInterceptor.failLockMinutes() + "分钟后再试");
        }

        if (!PasswordUtil.matches(password, user.getSecondaryPassword())) {
            failCount++;
            long ttl = TimeUnit.MINUTES.toSeconds(SecondaryLockInterceptor.failLockMinutes());
            redisUtil.set(failKey, String.valueOf(failCount), ttl, TimeUnit.SECONDS);
            int remaining = SecondaryLockInterceptor.maxFailCount() - failCount;
            throw new RuntimeException("二级密码错误，还剩" + Math.max(0, remaining) + "次机会");
        }

        // 验证成功，清除失败计数
        redisUtil.delete(failKey);
    }

    @Override
    public List<Long> getSecondaryLockMenuIds(long userId) {
        return secondaryLockMenuResolver.getLockedMenuIds(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveSecondaryLockMenus(long userId, List<Long> menuIds, String secondaryPassword) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        if (!StringUtils.hasText(user.getSecondaryPassword())) {
            throw new RuntimeException("未设置二级密码，无法保存菜单锁");
        }
        // 修改菜单锁必须验证二级密码
        verifySecondaryPasswordOrThrow(user, secondaryPassword);

        // 清除旧数据
        LambdaQueryWrapper<UserSecondaryLockMenuEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserSecondaryLockMenuEntity::getUserId, userId);
        lockMenuMapper.delete(wrapper);

        // 批量插入新数据（去重）
        if (menuIds != null && !menuIds.isEmpty()) {
            List<Long> uniqueIds = menuIds.stream().distinct().toList();
            for (Long menuId : uniqueIds) {
                UserSecondaryLockMenuEntity entity = new UserSecondaryLockMenuEntity();
                entity.setUserId(userId);
                entity.setMenuId(menuId);
                entity.fillCreateCommonField(userId);
                lockMenuMapper.insert(entity);
            }
        }
    }

    @Override
    public void sendResetSecondaryPasswordCode(long userId, String ip) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        String email = user.getEmail();
        if (!StringUtils.hasText(email)) {
            throw new RuntimeException("未绑定邮箱，无法找回二级密码");
        }

        sendEmailCode(EmailPurpose.RESET_SECONDARY_PASSWORD, email, ip, "重置二级密码验证码", "secondary_pwd");
    }

    @Override
    public void resetSecondaryPassword(long userId, String code, String password) {
        // 先校验新密码，避免无效提交消耗已发送的邮箱验证码。
        SecondaryPasswordPolicy.validate(password);
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        String email = user.getEmail();
        if (!StringUtils.hasText(email)) {
            throw new RuntimeException("未绑定邮箱");
        }

        authAttempts.consumeEmailCode(EmailPurpose.RESET_SECONDARY_PASSWORD, email, code);

        user.setSecondaryPassword(PasswordUtil.hashPassword(password));
        user.setUpdateTime(LocalDateTime.now());
        user.setUpdateUser(user.getId());
        userMapper.updateById(user);
    }
}
