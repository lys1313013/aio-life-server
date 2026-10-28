package top.aiolife.record.service;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import top.aiolife.sso.service.SecondaryLockPolicy;
import top.aiolife.sso.service.SecondaryLockGuard;
import top.aiolife.system.service.MenuClient;
import top.aiolife.system.service.MenuVisualService;

/** 聚合摘要仍要求所有来源业务的菜单权限及二级锁。 */
@Component
@RequiredArgsConstructor
public class HomeSectionAccessGuard {
    private final MenuVisualService visuals;
    private final SecondaryLockGuard locks;

    public void check(long userId, String... requiredMenus) {
        Set<String> accessible = Stream.of(MenuClient.WEB, MenuClient.MOBILE)
                .flatMap(client -> visuals.get(userId, client).menus().stream())
                .map(menu -> SecondaryLockPolicy.canonicalMenu(menu.path())).collect(Collectors.toSet());
        if (!accessible.containsAll(Arrays.asList(requiredMenus))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问此卡片的业务数据");
        }
        locks.checkMenus(userId, requiredMenus);
    }
}
