package top.aiolife.system.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import top.aiolife.system.mapper.ISysMenuMapper;
import top.aiolife.system.mapper.IUserQuickNavMapper;
import top.aiolife.system.pojo.entity.SysMenuEntity;
import top.aiolife.system.pojo.entity.UserQuickNavEntity;
import top.aiolife.system.pojo.req.QuickNavSaveReq;
import top.aiolife.system.service.impl.QuickNavServiceImpl;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class QuickNavClientStatusTest {
    @Test void 读取按端过滤且移动端保存不丢Web独有配置() {
        var rows = mock(IUserQuickNavMapper.class);
        var menus = mock(ISysMenuMapper.class);
        var access = mock(IMenuService.class);
        var service = new QuickNavServiceImpl(rows, menus, access, new ObjectMapper());
        var webRow = new UserQuickNavEntity(); webRow.setMenuId(1L); webRow.setEnabled(1); webRow.setSortOrder(0);
        var mobileRow = new UserQuickNavEntity(); mobileRow.setMenuId(2L); mobileRow.setEnabled(1); mobileRow.setSortOrder(1);
        when(rows.selectList(any())).thenReturn(List.of(webRow, mobileRow));
        var webMenu = new SysMenuEntity(); webMenu.setId(1L); webMenu.setPath("/web");
        var mobileMenu = new SysMenuEntity(); mobileMenu.setId(2L); mobileMenu.setPath("/mobile");
        when(menus.selectList(any())).thenReturn(List.of(webMenu, mobileMenu));
        when(access.getAccessibleMenuIds(List.of("user"), MenuClient.WEB)).thenReturn(Set.of(1L));
        when(access.getAccessibleMenuIds(List.of("user"), MenuClient.MOBILE)).thenReturn(Set.of(2L));
        assertEquals(List.of(1L), service.listMy(7L, List.of("user"), MenuClient.WEB).stream().map(m -> m.getMenuId()).toList());
        assertEquals(List.of(2L), service.listMy(7L, List.of("user"), MenuClient.MOBILE).stream().map(m -> m.getMenuId()).toList());
        var req = new QuickNavSaveReq(); req.setItems(List.of());
        service.saveMy(7L, List.of("user"), req, MenuClient.MOBILE);
        var inserted = ArgumentCaptor.forClass(UserQuickNavEntity.class);
        verify(rows).insert(inserted.capture());
        assertEquals(1L, inserted.getValue().getMenuId());
        assertEquals(7L, inserted.getValue().getUserId());
        var invalid = new QuickNavSaveReq.Item(); invalid.setMenuId(1L); invalid.setSortOrder(0); invalid.setEnabled(1);
        req.setItems(List.of(invalid));
        assertThrows(IllegalArgumentException.class, () -> service.saveMy(7L, List.of("user"), req, MenuClient.MOBILE));
    }
}
