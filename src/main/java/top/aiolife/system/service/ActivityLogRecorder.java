package top.aiolife.system.service;

import cn.hutool.http.useragent.UserAgentUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import top.aiolife.sso.mapper.UserMapper;
import top.aiolife.sso.pojo.entity.UserEntity;
import top.aiolife.system.mapper.ActivityLogMapper;
import top.aiolife.system.mapper.ISysMenuMapper;
import top.aiolife.system.pojo.entity.ActivityLogEntity;
import top.aiolife.system.pojo.entity.SysMenuEntity;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ActivityLogRecorder {
    private final ActivityLogMapper logMapper;
    private final UserMapper userMapper;
    private final ISysMenuMapper menuMapper;
    private final ObjectMapper objectMapper;
    private volatile List<SysMenuEntity> menus = List.of();
    private volatile long menusExpireAt;

    /** 与业务事务隔离，失败操作也保留记录。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(ActivityLogEntity row, String pagePath, String userAgent) {
        if (row.getUserId() != null) {
            var user = userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                    .select(UserEntity::getId, UserEntity::getUsername, UserEntity::getNickname)
                    .eq(UserEntity::getId, row.getUserId()));
            if (user != null) {
                row.setUsername(user.getUsername());
                row.setNickname(user.getNickname());
            }
        }
        row.setBrowser(userAgent == null || userAgent.isBlank() ? "未知" :
                UserAgentUtil.parse(userAgent).getBrowser().getName());
        if ("OPERATION".equals(row.getLogType())) row.setFunctionName(resolveTitle(pagePath, row.getFunctionName()));
        row.setUsername(limit(row.getUsername(), 100));
        row.setNickname(limit(row.getNickname(), 100));
        row.setIpAddress(limit(row.getIpAddress(), 64));
        row.setBrowser(limit(row.getBrowser(), 100));
        row.setFunctionName(limit(row.getFunctionName(), 200));
        row.setRequestPath(limit(row.getRequestPath(), 255));
        logMapper.insert(row);
    }

    private String resolveTitle(String pagePath, String fallback) {
        if (pagePath == null || !pagePath.startsWith("/")) return fallback;
        if (System.currentTimeMillis() >= menusExpireAt) {
            menus = menuMapper.selectList(new LambdaQueryWrapper<SysMenuEntity>()
                    .eq(SysMenuEntity::getStatus, 1)).stream()
                    .filter(menu -> menu.getPath() != null && menu.getComponent() != null
                            && !"BasicLayout".equals(menu.getComponent()))
                    .sorted(Comparator.comparingInt((SysMenuEntity menu) -> menu.getPath().length()).reversed()).toList();
            menusExpireAt = System.currentTimeMillis() + 60_000;
        }
        for (var menu : menus) {
            if (pagePath.equals(menu.getPath()) || pagePath.startsWith(menu.getPath() + "/")) {
                try {
                    return objectMapper.readTree(menu.getMeta()).path("title").asText(fallback);
                } catch (Exception ignored) {
                    return fallback;
                }
            }
        }
        return fallback;
    }

    static String limit(String value, int length) {
        return value == null ? "" : value.substring(0, Math.min(value.length(), length));
    }
}
