package top.aiolife.record.service;

import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import top.aiolife.sso.service.SecondaryLockGuard;
import top.aiolife.system.pojo.vo.MenuVisualVO;
import top.aiolife.system.pojo.vo.MenuVisualsVO;
import top.aiolife.system.service.MenuClient;
import top.aiolife.system.service.MenuVisualService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class HomeSectionAccessGuardTest {
    private final MenuVisualService visuals = mock(MenuVisualService.class);
    private final SecondaryLockGuard locks = mock(SecondaryLockGuard.class);
    private final HomeSectionAccessGuard guard = new HomeSectionAccessGuard(visuals, locks);

    @Test
    void testFinance_所有来源业务菜单必须同时可访问() {
        when(visuals.get(7, MenuClient.WEB)).thenReturn(menus("/finance/dashboard", "/finance/income"));
        when(visuals.get(7, MenuClient.MOBILE)).thenReturn(menus());
        var error = assertThrows(ResponseStatusException.class,
                () -> guard.check(7, "/finance/dashboard", "/finance/income", "/finance/expense"));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verifyNoInteractions(locks);
    }

    @Test
    void testFinance_跨端路径归一化后检查全部业务锁() {
        when(visuals.get(7, MenuClient.WEB)).thenReturn(menus("/finance/dashboard"));
        when(visuals.get(7, MenuClient.MOBILE)).thenReturn(menus("/finance-management/income", "/finance-management/expense"));
        guard.check(7, "/finance/dashboard", "/finance/income", "/finance/expense");
        verify(locks).checkMenus(7, "/finance/dashboard", "/finance/income", "/finance/expense");
    }

    @Test
    void testMemo_移动菜单别名保留同一业务锁且锁失败直接传播() {
        when(visuals.get(7, MenuClient.WEB)).thenReturn(menus());
        when(visuals.get(7, MenuClient.MOBILE)).thenReturn(menus("/my-hub/memo"));
        var locked = new ResponseStatusException(HttpStatus.LOCKED);
        doThrow(locked).when(locks).checkMenus(7, "/record/memo");
        assertSame(locked, assertThrows(ResponseStatusException.class, () -> guard.check(7, "/record/memo")));
        verify(locks).checkMenus(7, "/record/memo");
    }

    @Test
    void testIncome_只有收入菜单也可独立访问且只检查收入锁() {
        when(visuals.get(7, MenuClient.WEB)).thenReturn(menus("/finance/income"));
        when(visuals.get(7, MenuClient.MOBILE)).thenReturn(menus());
        guard.check(7, "/finance/income");
        verify(locks).checkMenus(7, "/finance/income");
        var error = assertThrows(ResponseStatusException.class, () -> guard.check(7, "/finance/expense"));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
    }

    @Test
    void testExpense_移动端仅支出权限也可独立访问() {
        when(visuals.get(7, MenuClient.WEB)).thenReturn(menus());
        when(visuals.get(7, MenuClient.MOBILE)).thenReturn(menus("/finance-management/expense"));
        guard.check(7, "/finance/expense");
        verify(locks).checkMenus(7, "/finance/expense");
        assertThrows(ResponseStatusException.class,
                () -> guard.check(7, "/finance/dashboard", "/finance/income", "/finance/expense"));
    }

    private MenuVisualsVO menus(String... paths) {
        return new MenuVisualsVO(Arrays.stream(paths).map(path -> new MenuVisualVO(null, path, "lucide:file", null)).toList(), Map.of());
    }
}
