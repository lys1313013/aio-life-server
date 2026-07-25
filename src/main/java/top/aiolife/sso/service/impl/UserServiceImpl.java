package top.aiolife.sso.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import top.aiolife.core.query.CommonQuery;
import top.aiolife.core.resq.PageResp;
import top.aiolife.record.service.IMailService;
import top.aiolife.record.util.RedisUtil;
import top.aiolife.sso.convertor.UserConvertor;
import top.aiolife.sso.mapper.LoginLogMapper;
import top.aiolife.sso.mapper.UserMapper;
import top.aiolife.sso.pojo.entity.LoginLogEntity;
import top.aiolife.sso.pojo.entity.UserEntity;
import top.aiolife.sso.pojo.req.ChangePasswordReq;
import top.aiolife.sso.pojo.req.LoginReq;
import top.aiolife.sso.pojo.req.RegisterReq;
import top.aiolife.sso.pojo.req.ResetPasswordReq;
import top.aiolife.sso.pojo.vo.UserBasicInfoVO;
import top.aiolife.sso.pojo.vo.UserInfoVO;
import top.aiolife.sso.pojo.vo.UserLoginVO;
import top.aiolife.sso.pojo.vo.UserVO;
import top.aiolife.sso.service.IUserService;
import top.aiolife.sso.util.PasswordUtil;
import cn.hutool.core.date.DateUtil;
import org.springframework.beans.factory.annotation.Value;

import top.aiolife.sso.interceptor.SecondaryLockInterceptor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.IntStream;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
    private final LoginLogMapper loginLogMapper;
    private final IMailService mailService;
    private final RedisUtil redisUtil;

    @Value("${spring.auth.code.interval-seconds:180}")
    private long intervalSeconds;

    @Value("${spring.auth.code.ip-limit-day:20}")
    private long ipLimitDay;

    @Value("${spring.auth.code.email-limit-day:10}")
    private long emailLimitDay;

    @Value("${spring.auth.code.total-limit-day:1000}")
    private long totalLimitDay;

    @Override
    public UserLoginVO login(LoginReq loginReq, String ip) {
        LambdaQueryWrapper<UserEntity> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(UserEntity::getUsername, loginReq.getUsername());

        UserEntity userEntity = userMapper.selectOne(lambdaQueryWrapper);

        // 记录登录日志
        LoginLogEntity loginLogEntity = new LoginLogEntity();
        loginLogEntity.setUsername(loginReq.getUsername());
        loginLogEntity.setIpAddress(ip);

        if (userEntity == null) {
            loginLogEntity.setPassword(loginReq.getPassword());
            loginLogMapper.insert(loginLogEntity);
            throw new RuntimeException("用户名或密码错误");
        }

        // 校验密码
        String encryptedPassword = PasswordUtil.encryptPassword(loginReq.getPassword(), userEntity.getPasswordSalt());
        if (!userEntity.getPassword().equals(encryptedPassword)) {
            loginLogEntity.setPassword(loginReq.getPassword());
            loginLogMapper.insert(loginLogEntity);
            throw new RuntimeException("用户名或密码错误");
        }

        loginLogEntity.setUserId(userEntity.getId());
        loginLogMapper.insert(loginLogEntity);
        StpUtil.login(userEntity.getId());
        String token = StpUtil.getTokenValue();

        // 为了兼容前端 img 标签直接使用 Cookie 访问带鉴权的图片
        // 需要在写入浏览器的 Cookie 时，手动加上配置的 token-prefix (Bearer)
        // 这样浏览器发起的 Cookie 中就包含了完整的 "Bearer xxxx" 格式，完美通过 Sa-Token 校验
        String tokenName = StpUtil.getTokenName();
        String tokenPrefix = cn.dev33.satoken.SaManager.getConfig().getTokenPrefix();
        String cookieValue = (tokenPrefix != null ? tokenPrefix + " " : "") + token;
        // 获取默认的 Cookie 超时时间配置
        int timeout = (int) cn.dev33.satoken.SaManager.getConfig().getTimeout();
        cn.dev33.satoken.context.SaHolder.getResponse().addCookie(tokenName, cookieValue, "/", null, timeout);

        UserLoginVO userLoginVO = new UserLoginVO();
        userLoginVO.setId(userEntity.getId());
        userLoginVO.setRealName(userEntity.getNickname());
        userLoginVO.setUsername(userEntity.getNickname());
        userLoginVO.setRoles(StringUtils.hasText(userEntity.getRole()) ? Arrays.asList(userEntity.getRole().split(",")) : Collections.singletonList("user"));
        userLoginVO.setAccessToken(token);
        return userLoginVO;
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
        userInfoVO.setAvatar(userEntity.getAvatar());
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
        vo.setAvatar(userEntity.getAvatar());
        return vo;
    }

    @Override
    @CacheEvict(value = {"userInfo", "userBasicInfo"}, key = "#userEntity.id")
    public void updateUser(UserEntity userEntity) {
        // 禁止通过此接口修改密码
        userEntity.setPassword(null);
        userEntity.setPasswordSalt(null);
        userMapper.updateById(userEntity);
    }

    @Override
    public void changePassword(long userId, ChangePasswordReq changePasswordReq) {
        UserEntity userEntity = userMapper.selectById(userId);
        if (userEntity == null) {
            throw new RuntimeException("用户不存在");
        }
        String encryptedPassword = PasswordUtil.encryptPassword(changePasswordReq.getOldPassword(), userEntity.getPasswordSalt());
        if (!userEntity.getPassword().equals(encryptedPassword)) {
            throw new RuntimeException("旧密码错误");
        }
        String salt = PasswordUtil.getSalt();
        userEntity.setPasswordSalt(salt);
        userEntity.setPassword(PasswordUtil.encryptPassword(changePasswordReq.getNewPassword(), salt));
        userMapper.updateById(userEntity);
    }

    @Override
    public PageResp<UserVO> getUserList(CommonQuery query) {
        Page<UserEntity> page = new Page<>(query.getPage(), query.getPageSize());
        LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<>();
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

    @Override
    public void addUser(UserEntity userEntity) {
        if (!StringUtils.hasText(userEntity.getPassword())) {
            userEntity.setPassword("123456");
        }
        String salt = PasswordUtil.getSalt();
        userEntity.setPasswordSalt(salt);
        userEntity.setPassword(PasswordUtil.encryptPassword(userEntity.getPassword(), salt));
        userMapper.insert(userEntity);
    }

    @Override
    @CacheEvict(value = "userInfo", key = "#id")
    public void deleteUser(Long id) {
        userMapper.deleteById(id);
    }

    @Override
    public void sendRegisterCode(String email, String ip) {
        // 简单校验邮箱格式
        if (!email.contains("@")) {
            throw new RuntimeException("邮箱格式不正确");
        }
        // 校验邮箱是否已注册
        LambdaQueryWrapper<UserEntity> emailWrapper = new LambdaQueryWrapper<>();
        emailWrapper.eq(UserEntity::getEmail, email);
        if (userMapper.selectCount(emailWrapper) > 0) {
            throw new RuntimeException("邮箱已被注册");
        }

        checkRateLimit(email, ip);
        
        // 生成验证码
        String code = RandomUtil.randomNumbers(6);
        // 存入Redis，5分钟有效
        redisUtil.set("register:code:" + email, code, 5, TimeUnit.MINUTES);
        
        // 更新频率限制记录
        updateRateLimit(email, ip);

        // 发送邮件
        mailService.sendSimpleEmail(email, "注册验证码", "您的验证码是：" + code + "，有效期5分钟。", "register", ip);
    }

    @Override
    public void sendResetPasswordCode(String email, String ip) {
        // 简单校验邮箱格式
        if (!email.contains("@")) {
            throw new RuntimeException("邮箱格式不正确");
        }
        // 校验邮箱是否已注册
        LambdaQueryWrapper<UserEntity> emailWrapper = new LambdaQueryWrapper<>();
        emailWrapper.eq(UserEntity::getEmail, email);
        if (userMapper.selectCount(emailWrapper) == 0) {
            throw new RuntimeException("邮箱未注册");
        }

        checkRateLimit(email, ip);

        // 生成验证码
        String code = RandomUtil.randomNumbers(6);
        // 存入Redis，5分钟有效
        redisUtil.set("reset:code:" + email, code, 5, TimeUnit.MINUTES);
        
        // 更新频率限制记录
        updateRateLimit(email, ip);
        
        // 发送邮件
        mailService.sendSimpleEmail(email, "重置密码验证码", "您的验证码是：" + code + "，有效期5分钟。", "reset_pwd", ip);
    }

    /**
     * 检查频率限制
     */
    private void checkRateLimit(String email, String ip) {
        // 频率限制：3分钟内只能发送一次（使用配置的 intervalSeconds）
        String lockKey = "code:lock:" + email;
        if (redisUtil.hasKey(lockKey)) {
            throw new RuntimeException("操作过于频繁，请" + (intervalSeconds / 60) + "分钟后再试");
        }

        String today = DateUtil.today();
        String ipKey = "code:limit:ip:" + today + ":" + ip;
        String emailKey = "code:limit:email:" + today + ":" + email;
        String totalKey = "code:limit:total:" + today;

        // 全局单日总限制
        String totalCountStr = redisUtil.get(totalKey);
        if (totalCountStr != null && Long.parseLong(totalCountStr) >= totalLimitDay) {
            throw new RuntimeException("今日发送验证码总次数已达上限，请明天再试");
        }

        // IP单日限制
        String ipCountStr = redisUtil.get(ipKey);
        if (ipCountStr != null && Long.parseLong(ipCountStr) >= ipLimitDay) {
            throw new RuntimeException("当前IP今日发送次数已达上限");
        }

        // 邮箱单日限制
        String emailCountStr = redisUtil.get(emailKey);
        if (emailCountStr != null && Long.parseLong(emailCountStr) >= emailLimitDay) {
            throw new RuntimeException("当前邮箱今日发送次数已达上限");
        }
    }

    /**
     * 更新频率限制记录
     */
    private void updateRateLimit(String email, String ip) {
        // 设置间隔锁
        String lockKey = "code:lock:" + email;
        redisUtil.set(lockKey, "1", intervalSeconds, TimeUnit.SECONDS);

        String today = DateUtil.today();
        String ipKey = "code:limit:ip:" + today + ":" + ip;
        String emailKey = "code:limit:email:" + today + ":" + email;
        String totalKey = "code:limit:total:" + today;

        // 更新全局计数
        Long totalCount = redisUtil.increment(totalKey, 1);
        if (totalCount != null && totalCount == 1) {
            redisUtil.expire(totalKey, 1, TimeUnit.DAYS);
        }

        // 更新IP计数
        Long ipCount = redisUtil.increment(ipKey, 1);
        if (ipCount != null && ipCount == 1) {
            redisUtil.expire(ipKey, 1, TimeUnit.DAYS);
        }

        // 更新邮箱计数
        Long emailCount = redisUtil.increment(emailKey, 1);
        if (emailCount != null && emailCount == 1) {
            redisUtil.expire(emailKey, 1, TimeUnit.DAYS);
        }
    }

    @Override
    public void resetPassword(ResetPasswordReq resetPasswordReq) {
        // 校验验证码
        String code = redisUtil.get("reset:code:" + resetPasswordReq.getEmail());
        if (code == null || !code.equals(resetPasswordReq.getCode())) {
            throw new RuntimeException("验证码错误或已过期");
        }

        LambdaQueryWrapper<UserEntity> emailWrapper = new LambdaQueryWrapper<>();
        emailWrapper.eq(UserEntity::getEmail, resetPasswordReq.getEmail());
        UserEntity userEntity = userMapper.selectOne(emailWrapper);
        if (userEntity == null) {
            throw new RuntimeException("用户不存在");
        }

        String salt = PasswordUtil.getSalt();
        userEntity.setPasswordSalt(salt);
        userEntity.setPassword(PasswordUtil.encryptPassword(resetPasswordReq.getPassword(), salt));
        userMapper.updateById(userEntity);

        // 删除验证码
        redisUtil.delete("reset:code:" + resetPasswordReq.getEmail());
    }

    @Override
    public void register(RegisterReq registerReq) {
        // 校验验证码
        String code = redisUtil.get("register:code:" + registerReq.getEmail());
        if (code == null || !code.equals(registerReq.getCode())) {
            throw new RuntimeException("验证码错误或已过期");
        }
        
        // 校验用户名是否已存在
        LambdaQueryWrapper<UserEntity> usernameWrapper = new LambdaQueryWrapper<>();
        usernameWrapper.eq(UserEntity::getUsername, registerReq.getUsername());
        if (userMapper.selectCount(usernameWrapper) > 0) {
            throw new RuntimeException("用户名已存在");
        }

        // 校验邮箱是否已存在（双重检查）
        LambdaQueryWrapper<UserEntity> emailWrapper = new LambdaQueryWrapper<>();
        emailWrapper.eq(UserEntity::getEmail, registerReq.getEmail());
        if (userMapper.selectCount(emailWrapper) > 0) {
            throw new RuntimeException("邮箱已被注册");
        }

        UserEntity userEntity = new UserEntity();
        userEntity.setUsername(registerReq.getUsername());
        userEntity.setNickname(registerReq.getUsername()); // 默认昵称同用户名
        userEntity.setEmail(registerReq.getEmail());
        userEntity.setPassword(registerReq.getPassword());
        userEntity.setRole("user"); // 默认普通用户
        
        String salt = PasswordUtil.getSalt();
        userEntity.setPasswordSalt(salt);
        userEntity.setPassword(PasswordUtil.encryptPassword(userEntity.getPassword(), salt));
        
        userMapper.insert(userEntity);
        
        // 删除验证码
        redisUtil.delete("register:code:" + registerReq.getEmail());
    }

    @Override
    public boolean hasSecondaryPassword(long userId) {
        UserEntity user = userMapper.selectById(userId);
        return user != null && org.springframework.util.StringUtils.hasText(user.getSecondaryPassword());
    }

    @Override
    public void setSecondaryPassword(long userId, String password, String oldPassword) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        // 如果已有二级密码，需要验证旧密码
        if (org.springframework.util.StringUtils.hasText(user.getSecondaryPassword())) {
            if (!org.springframework.util.StringUtils.hasText(oldPassword)) {
                throw new RuntimeException("请提供旧二级密码");
            }
            String encryptedOld = PasswordUtil.encryptPassword(oldPassword, user.getSecondaryPasswordSalt());
            if (!user.getSecondaryPassword().equals(encryptedOld)) {
                throw new RuntimeException("旧二级密码错误");
            }
        }

        String salt = PasswordUtil.getSalt();
        user.setSecondaryPasswordSalt(salt);
        user.setSecondaryPassword(PasswordUtil.encryptPassword(password, salt));
        userMapper.updateById(user);
    }

    @Override
    public String verifySecondaryPassword(long userId, String password, String menuPath) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        if (!org.springframework.util.StringUtils.hasText(user.getSecondaryPassword())) {
            throw new RuntimeException("未设置二级密码");
        }

        // 检查失败次数
        String failKey = SecondaryLockInterceptor.failCountKey(userId);
        String failCountStr = redisUtil.get(failKey);
        int failCount = failCountStr == null ? 0 : Integer.parseInt(failCountStr);
        if (failCount >= SecondaryLockInterceptor.maxFailCount()) {
            throw new RuntimeException("尝试次数过多，请" + SecondaryLockInterceptor.failLockMinutes() + "分钟后再试");
        }

        String encrypted = PasswordUtil.encryptPassword(password, user.getSecondaryPasswordSalt());
        if (!user.getSecondaryPassword().equals(encrypted)) {
            failCount++;
            long ttl = failCount >= SecondaryLockInterceptor.maxFailCount()
                    ? TimeUnit.MINUTES.toSeconds(SecondaryLockInterceptor.failLockMinutes())
                    : TimeUnit.MINUTES.toSeconds(SecondaryLockInterceptor.failLockMinutes());
            redisUtil.set(failKey, String.valueOf(failCount), ttl, TimeUnit.SECONDS);
            int remaining = SecondaryLockInterceptor.maxFailCount() - failCount;
            throw new RuntimeException("二级密码错误，还剩" + Math.max(0, remaining) + "次机会");
        }

        // 验证成功，清除失败计数
        redisUtil.delete(failKey);

        // 写入解锁凭证
        String unlockKey = SecondaryLockInterceptor.unlockKey(userId, menuPath);
        redisUtil.set(unlockKey, "1", SecondaryLockInterceptor.unlockTtlSeconds(), TimeUnit.SECONDS);

        return unlockKey;
    }
}
