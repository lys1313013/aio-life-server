package top.aiolife.core.cache;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import top.aiolife.sso.mapper.UserSecondaryLockMenuMapper;
import top.aiolife.sso.pojo.entity.UserSecondaryLockMenuEntity;
import top.aiolife.system.mapper.ISysMenuMapper;
import top.aiolife.system.pojo.entity.SysMenuEntity;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MenuDataCacheTest {
    @Test void 新配置无需提交后Redis失效即可被其它实例读取() {
        var locks = mock(UserSecondaryLockMenuMapper.class);
        var menus = mock(ISysMenuMapper.class);
        var row = new UserSecondaryLockMenuEntity(); row.setMenuId(7L);
        when(locks.selectForAccessControl(anyLong())).thenReturn(List.of(), List.of(row), List.of());
        var first = new MenuDataCache(locks, menus);
        var restarted = new MenuDataCache(locks, menus);
        assertEquals(List.of(), first.getLockedMenuIds(1));
        // 模拟数据库提交后进程直接退出，没有任何回调、Redis版本更新或缓存清理。
        assertEquals(List.of(7L), restarted.getLockedMenuIds(1));
        assertEquals(List.of(), first.getLockedMenuIds(1));
        verify(locks, times(3)).selectForAccessControl(1);
    }

    @Test void 菜单路径变化立即改变保护范围且数据库失败不会使用旧授权() {
        var locks = mock(UserSecondaryLockMenuMapper.class);
        var menus = mock(ISysMenuMapper.class);
        var row = new UserSecondaryLockMenuEntity(); row.setMenuId(7L);
        when(locks.selectForAccessControl(anyLong())).thenReturn(List.of(row));
        var old = new SysMenuEntity(); old.setId(7L); old.setPath("/old");
        var changed = new SysMenuEntity(); changed.setId(7L); changed.setPath("/new");
        when(menus.selectAllForAccessControl()).thenReturn(List.of(old), List.of(changed))
                .thenThrow(new IllegalStateException("database unavailable"));
        var guard = new SecondaryLockMenuCache(new MenuDataCache(locks, menus));
        assertEquals(Set.of("/old"), guard.getLockedPaths(1));
        assertEquals(Set.of("/new"), guard.getLockedPaths(1));
        assertThrows(IllegalStateException.class, () -> guard.getLockedPaths(1));
    }
}
