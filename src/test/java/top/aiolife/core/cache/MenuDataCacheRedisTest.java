package top.aiolife.core.cache;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import top.aiolife.record.util.RedisUtil;
import top.aiolife.sso.mapper.UserSecondaryLockMenuMapper;
import top.aiolife.sso.pojo.entity.UserSecondaryLockMenuEntity;
import top.aiolife.system.mapper.ISysMenuMapper;
import top.aiolife.system.pojo.entity.SysMenuEntity;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 仅连接显式提供的隔离本地测试 Redis；默认不运行，不连接应用配置中的 Redis。 */
@EnabledIfEnvironmentVariable(named = "MENU_CACHE_REDIS_TEST_PORT", matches = "\\d+")
class MenuDataCacheRedisTest {
    @Test
    void redis_实际序列化跨实例复用五小时TTL和修改失效() {
        int port = Integer.parseInt(System.getenv("MENU_CACHE_REDIS_TEST_PORT"));
        var factory = new LettuceConnectionFactory("127.0.0.1", port);
        factory.afterPropertiesSet();
        factory.start();
        try {
            var template = new StringRedisTemplate(factory);
            var redis = new RedisUtil(template, new ObjectMapper().findAndRegisterModules());
            var locks = mock(UserSecondaryLockMenuMapper.class);
            var menus = mock(ISysMenuMapper.class);
            var lock = new UserSecondaryLockMenuEntity(); lock.setMenuId(9223372036854775807L);
            when(locks.selectList(any(Wrapper.class))).thenReturn(List.of(lock), List.of());
            var menu = new SysMenuEntity(); menu.setId(9223372036854775807L); menu.setPath("/my-hub/memo");
            when(menus.selectList(any(Wrapper.class))).thenReturn(List.of(menu));
            var first = new MenuDataCache(locks, menus, redis);
            var second = new MenuDataCache(locks, menus, redis);
            assertEquals(List.of(Long.MAX_VALUE), first.getLockedMenuIds(991));
            assertEquals(List.of(Long.MAX_VALUE), second.getLockedMenuIds(991));
            assertEquals(Long.MAX_VALUE, first.getEnabledMenus().getFirst().getId());
            assertEquals("/my-hub/memo", second.getEnabledMenus().getFirst().getPath());
            verify(locks).selectList(any(Wrapper.class));
            verify(menus).selectList(any(Wrapper.class));
            for (String key : List.of("aio:menu-cache:v1:locks:991:data:0", "aio:menu-cache:v1:enabled:data:0")) {
                Long ttl = template.getExpire(key, TimeUnit.SECONDS);
                assertNotNull(ttl);
                assertTrue(ttl > 17_900 && ttl <= 18_000, "TTL 应为 5 小时: " + ttl);
            }
            first.evictLockedMenuIds(991);
            assertEquals(List.of(), second.getLockedMenuIds(991));
            assertEquals(List.of(), first.getLockedMenuIds(991));
            verify(locks, times(2)).selectList(any(Wrapper.class));
            first.evictMenus();
            second.getEnabledMenus();
            verify(menus, times(2)).selectList(any(Wrapper.class));
        } finally {
            factory.destroy();
        }
    }
}
