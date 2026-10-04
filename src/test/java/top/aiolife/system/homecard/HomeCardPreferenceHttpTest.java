package top.aiolife.system.homecard;

import cn.dev33.satoken.stp.StpUtil;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import top.aiolife.system.api.HomeCardPreferenceController;
import top.aiolife.system.service.HomeCardPreferenceService;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class HomeCardPreferenceHttpTest {
    @Test void identity_忽略客户端用户字段并始终使用登录态() throws Exception {
        var service = mock(HomeCardPreferenceService.class);
        when(service.toggle(anyLong(), anyString(), anyBoolean())).thenReturn(List.of());
        var mvc = MockMvcBuilders.standaloneSetup(new HomeCardPreferenceController(service)).build();
        try (MockedStatic<StpUtil> login = mockStatic(StpUtil.class)) {
            login.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            mvc.perform(put("/home/cards/section.goal").contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":false,\"userId\":8}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rscode").value("0"));
            verify(service).toggle(7, "section.goal", false);
            mvc.perform(get("/home/cards")).andExpect(status().isOk());
            verify(service).get(7);
            mvc.perform(delete("/home/cards")).andExpect(status().isOk());
            verify(service).reset(7);
        }
    }
    @Test void validation_缺失开关或空排序被拒绝且不调用业务写入() throws Exception {
        var service = mock(HomeCardPreferenceService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new HomeCardPreferenceController(service)).build();
        mvc.perform(put("/home/cards/section.goal").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
        mvc.perform(put("/home/cards/order").contentType(MediaType.APPLICATION_JSON).content("{\"group\":\"section\",\"keys\":[]}"))
            .andExpect(status().isBadRequest());
        mvc.perform(put("/home/cards/order").contentType(MediaType.APPLICATION_JSON).content("{\"group\":\"section\",\"keys\":[null]}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
