package top.aiolife.security;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import top.aiolife.core.cache.SecondaryLockMenuCache;
import top.aiolife.core.cache.SecondaryLockPolicy;
import top.aiolife.core.exception.SecondaryLockRequiredException;
import top.aiolife.mcp.invoker.McpToolInvoker;
import top.aiolife.mcp.registry.McpToolRegistry;
import top.aiolife.record.util.RedisUtil;
import top.aiolife.sso.interceptor.SecondaryLockInterceptor;
import top.aiolife.sso.mapper.UserSecondaryLockMenuMapper;
import top.aiolife.sso.pojo.entity.UserSecondaryLockMenuEntity;
import top.aiolife.sso.service.SecondaryLockGuard;
import top.aiolife.sso.util.RequestLoginContext;
import top.aiolife.system.mapper.ISysMenuMapper;
import top.aiolife.system.pojo.entity.SysMenuEntity;

import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class SecondaryLockBoundaryTest {
    @Test
    void menuMapping_coversRestAliasesAndAllRegisteredTools() {
        assertTrue(SecondaryLockPolicy.requestMenus("/tasks").contains("/task/todo"));
        assertTrue(SecondaryLockPolicy.requestMenus("/password/list").contains("/record/password"));
        assertTrue(SecondaryLockPolicy.requestMenus("/dashboard/card/READ").contains("/record/weread"));
        assertFalse(SecondaryLockPolicy.requestMenus("/tasks-other").contains("/task/todo"));
        assertFalse(SecondaryLockPolicy.requestMenus("/timeTrackerCategory/admin/1").contains("/time/my-categories"));
        for (String tool : List.of("task_list", "task_detail_save", "goal_query", "goal_progress_update",
                "time_record_save", "time_record_queryByDateRange", "time_tracker_category_list", "thought_save",
                "b_video_query", "b_video_statistics", "movie_query", "read_record_query", "anniversary_query",
                "dashboard_cards", "douban_wishlist_add")) {
            assertTrue(SecondaryLockPolicy.toolMenus(tool).contains("/mcp/tools"));
        }
        assertThrows(IllegalArgumentException.class, () -> SecondaryLockPolicy.toolMenus("unmapped_tool"));
    }

    @Test
    void parentAndLeafLocks_useDatabaseHierarchyAndOriginalUnlockKeys() {
        var locks = mock(UserSecondaryLockMenuMapper.class); var menus = mock(ISysMenuMapper.class);
        var parent = new UserSecondaryLockMenuEntity(); parent.setMenuId(1L);
        var child = new UserSecondaryLockMenuEntity(); child.setMenuId(2L);
        when(locks.selectList(any(Wrapper.class))).thenReturn(List.of(parent, child));
        when(menus.selectList(any(Wrapper.class))).thenReturn(List.of(menu(1L, null, "/record"),
                menu(2L, 1L, "/my-hub/honor")));
        var cache = new SecondaryLockMenuCache(locks, menus);
        assertEquals(Set.of("/record", "/my-hub/honor"), cache.findMatchedPaths(11L, "/honorRecords/7"));
        assertEquals(Set.of("/record", "/my-hub/honor"), cache.findLockedMenus(11L, Set.of("/my-hub/honor")));
        var redis = mock(RedisUtil.class);
        when(redis.hasKey("secondary:unlock:11:/record")).thenReturn(true);
        var guard = new SecondaryLockGuard(cache, redis);
        assertThrows(SecondaryLockRequiredException.class, () -> guard.checkMenus(11L, "/my-hub/honor"));
        when(redis.hasKey("secondary:unlock:11:/my-hub/honor")).thenReturn(true);
        assertDoesNotThrow(() -> guard.checkMenus(11L, "/my-hub/honor"));
    }

    @Test
    void interceptor_normalizesEncodedAndMatrixPathsAndReturnsActualMenu() throws Exception {
        for (String uri : List.of("/api/tasks", "/api/%74asks", "/api/tasks;x=1")) {
            var cache = mock(SecondaryLockMenuCache.class); var redis = mock(RedisUtil.class);
            when(cache.findMatchedPaths(11L, "/tasks")).thenReturn(Set.of("/task/todo"));
            var interceptor = new SecondaryLockInterceptor(cache, redis, new ObjectMapper());
            try (var identity = mockStatic(RequestLoginContext.class)) {
                identity.when(RequestLoginContext::userIdOrNull).thenReturn(11L);
                var response = new MockHttpServletResponse();
                assertFalse(interceptor.preHandle(new MockHttpServletRequest("GET", uri), response, new Object()));
                assertTrue(response.getContentAsString().contains("/task/todo"));
                assertTrue(response.getContentAsString().contains("2001"));
            }
        }
    }

    @Test
    void mcpDenial_happensBeforeToolInvocation() throws Exception {
        var guard = mock(SecondaryLockGuard.class);
        doThrow(new SecondaryLockRequiredException("/task/todo")).when(guard).checkTool("task_list");
        var invoker = new McpToolInvoker(new ObjectMapper(), guard);
        var target = new DummyTool();
        var tool = new McpToolRegistry.RegisteredMcpTool("task_list", "dummy", target,
                DummyTool.class.getMethod("run"), null, null, null);
        var result = invoker.invoke(tool, java.util.Map.of(), null, null);
        assertTrue(result.isError()); assertFalse(target.called);
        verify(guard).checkTool("task_list");
    }

    private static SysMenuEntity menu(Long id, Long parent, String path) {
        var menu = new SysMenuEntity(); menu.setId(id); menu.setParentId(parent); menu.setPath(path); return menu;
    }

    public static class DummyTool {
        boolean called;
        public String run() { called = true; return "private"; }
    }
}
