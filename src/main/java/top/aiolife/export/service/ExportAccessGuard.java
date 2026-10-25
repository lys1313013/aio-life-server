package top.aiolife.export.service;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.stp.StpUtil;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import top.aiolife.core.cache.SecondaryLockPolicy;
import top.aiolife.core.exception.SecondaryLockRequiredException;
import top.aiolife.export.dataset.ExportDataset;
import top.aiolife.export.mapper.ExportSecurityMapper;
import top.aiolife.record.util.RedisUtil;
import top.aiolife.sso.interceptor.SecondaryLockInterceptor;
import top.aiolife.sso.pojo.entity.UserEntity;
import top.aiolife.system.pojo.entity.SysMenuEntity;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 导出专用授权边界，管理员也不获得他人任务所有权。 */
@Service
@RequiredArgsConstructor
public class ExportAccessGuard {
    private final ExportSecurityMapper security;
    private final RedisUtil redis;

    public long requestUser() {
        if (Boolean.TRUE.equals(SaHolder.getStorage().get("IS_API_KEY_AUTH")) || StpUtil.isSwitch())
            throw ExportException.denied();
        StpUtil.checkLogin();
        return StpUtil.getLoginIdAsLong();
    }

    public String sessionHash() {
        return ExportHash.of(StpUtil.getTokenValue());
    }

    public static String securityHash(UserEntity user) {
        return ExportHash.of(
                Objects.toString(user.getPassword(), "")
                        + ":"
                        + Objects.toString(user.getSecondaryPassword(), ""));
    }

    public boolean sessionActive(long userId, String hash) {
        return StpUtil.getTokenValueListByLoginId(userId).stream()
                .anyMatch(
                        token ->
                                ExportHash.of(token).equals(hash)
                                        && StpUtil.getLoginIdByToken(token) != null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public UserEntity user(long userId) {
        UserEntity user = security.user(userId);
        if (user == null) throw ExportException.denied();
        return user;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void check(long userId, List<ExportDataset> datasets, boolean requireUnlocked) {
        UserEntity user = security.user(userId);
        if (user == null) throw ExportException.denied();
        Map<Long, SysMenuEntity> menus =
                security.menus().stream().collect(Collectors.toMap(SysMenuEntity::getId, m -> m));
        Set<String> roles =
                Arrays.stream(Objects.toString(user.getRole(), "user").split(","))
                        .map(String::trim)
                        .collect(Collectors.toSet());
        Set<Long> locked = new HashSet<>(security.lockedMenus(userId));
        // 个人中心本身也可能被锁定，资料类数据没有业务菜单时同样不能绕过。
        Set<String> paths = new HashSet<>();
        paths.add("/profile");
        for (ExportDataset dataset : datasets) {
            if (!dataset.available()) throw ExportException.unavailable();
            paths.addAll(dataset.menus());
        }
        for (String path : paths) {
            List<SysMenuEntity> matches =
                    menus.values().stream()
                            .filter(
                                    m ->
                                            SecondaryLockPolicy.canonicalMenu(m.getPath())
                                                    .equals(
                                                            SecondaryLockPolicy.canonicalMenu(
                                                                    path)))
                            .toList();
            SysMenuEntity match =
                    matches.stream()
                            .filter(
                                    m ->
                                            accessible(m, menus, roles, false)
                                                    || accessible(m, menus, roles, true))
                            .findFirst()
                            .orElseThrow(ExportException::denied);
            if (requireUnlocked) checkLocks(match, menus, locked, userId);
        }
    }

    private boolean accessible(
            SysMenuEntity leaf, Map<Long, SysMenuEntity> menus, Set<String> roles, boolean mobile) {
        Set<Long> visited = new HashSet<>();
        SysMenuEntity cursor = leaf;
        while (cursor != null && visited.add(cursor.getId())) {
            if (!Integer.valueOf(1).equals(mobile ? cursor.getMobileStatus() : cursor.getStatus())
                    || !allowedRole(cursor.getRoles(), roles)) return false;
            if (cursor.getParentId() == null || cursor.getParentId() == 0L) return true;
            cursor = menus.get(cursor.getParentId());
        }
        return false;
    }

    private boolean allowedRole(String configured, Set<String> roles) {
        return roles.contains("admin")
                || !StringUtils.hasText(configured)
                || Arrays.stream(configured.split(",")).map(String::trim).anyMatch(roles::contains);
    }

    private void checkLocks(
            SysMenuEntity leaf, Map<Long, SysMenuEntity> menus, Set<Long> locked, long userId) {
        Set<Long> visited = new HashSet<>();
        for (SysMenuEntity cursor = leaf;
                cursor != null && visited.add(cursor.getId());
                cursor = menus.get(cursor.getParentId())) {
            if (locked.contains(cursor.getId())
                    && !redis.hasKey(SecondaryLockInterceptor.unlockKey(userId, cursor.getPath())))
                throw new SecondaryLockRequiredException(cursor.getPath());
        }
    }
}
