package top.aiolife.system.service;

import cn.dev33.satoken.stp.StpUtil;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import top.aiolife.sso.mapper.UserMapper;
import top.aiolife.system.api.MenuController;
import top.aiolife.system.pojo.vo.MenuRouteVO;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MenuVisualHttpTest {
    @Test void 视觉接口使用登录身份与客户端且保留字符串菜单ID() throws Exception {
        var menus = mock(IMenuService.class);
        var users = mock(UserMapper.class);
        var reading = new MenuRouteVO();
        reading.setId(9007199254740993L);
        reading.setPath("/record/read");
        reading.setMeta(Map.of("icon", "lucide:library"));
        reading.setIconColor("#427B8F");
        when(menus.getAccessibleMenuTree(List.of("user"), MenuClient.MOBILE)).thenReturn(List.of(reading));
        var service = new MenuVisualService(menus, users);
        var mvc = MockMvcBuilders.standaloneSetup(new MenuController(menus, users, service)).build();
        try (MockedStatic<StpUtil> login = mockStatic(StpUtil.class)) {
            login.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            mvc.perform(get("/menu/visuals").param("client", "mobile").param("userId", "8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.cards['section.reading'].menuId").value("9007199254740993"))
                .andExpect(jsonPath("$.data.cards['section.reading'].icon").value("lucide:library"))
                .andExpect(jsonPath("$.data.cards['section.reading'].iconColor").value("#427B8F"));
            verify(users).selectById(7L);
            verify(users, never()).selectById(8L);
        }
    }
}
