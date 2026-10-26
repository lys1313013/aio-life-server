package top.aiolife.record.api;

import cn.dev33.satoken.stp.StpUtil;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import top.aiolife.config.JsonConfig;
import top.aiolife.core.exception.ExceptionHandle;
import top.aiolife.core.exception.SecondaryLockRequiredException;
import top.aiolife.record.pojo.vo.HomeSectionVO;
import top.aiolife.record.mapper.HomeSectionMapper;
import top.aiolife.record.service.HomeSectionAccessGuard;
import org.springframework.test.web.servlet.MockMvc;
import top.aiolife.record.service.HomeSectionService;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class HomeSectionHttpTest {
    @Test void 身份取登录态且摘要ID为字符串并且没有正文() throws Exception {
        var service = mock(HomeSectionService.class);
        var memo = new HomeSectionVO.Memo();
        memo.setId(9007199254740993L);
        memo.setTitle("隐藏笔记");
        memo.setHiddenContent(true);
        memo.setUpdateTime(LocalDateTime.of(2026, 10, 22, 12, 0));
        when(service.memos(7L)).thenReturn(new HomeSectionVO.Items<>(List.of(memo), 300));
        var json = new JsonConfig().jacksonObjectMapper(new Jackson2ObjectMapperBuilder());
        var mvc = MockMvcBuilders.standaloneSetup(new HomeSectionController(service))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(json)).build();
        try (var login = mockStatic(StpUtil.class)) {
            login.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            mvc.perform(get("/home/sections/memo").param("userId", "99"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.items[0].id").value("9007199254740993"))
                    .andExpect(jsonPath("$.data.items[0].hiddenContent").value(true))
                    .andExpect(jsonPath("$.data.items[0].content").doesNotExist())
                    .andExpect(jsonPath("$.data.items[0].updateTime").value("2026-10-22 12:00:00"))
                    .andExpect(jsonPath("$.data.refreshInterval").value(300));
            verify(service).memos(7L);
            verify(service, never()).memos(99L);
        }
    }

    @Test void 视频与设备图片ID保持字符串且摘要不返回原始图片URL() throws Exception {
        var service = mock(HomeSectionService.class);
        var video = new HomeSectionVO.Video();
        video.setId(9007199254740993L);
        video.setCoverFileId("9007199254740995");
        video.setCoverState("READY");
        var device = new HomeSectionVO.Device();
        device.setId(9007199254740997L);
        device.setFileId("9007199254740999");
        device.setPurchaseDate(LocalDate.of(2026, 10, 22));
        when(service.videos(7)).thenReturn(new HomeSectionVO.Items<>(List.of(video), 300));
        when(service.devices(7)).thenReturn(new HomeSectionVO.Items<>(List.of(device), 600));
        var mvc = mvc(service);
        try (var login = mockStatic(StpUtil.class)) {
            login.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            mvc.perform(get("/home/sections/video"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.items[0].id").value("9007199254740993"))
                    .andExpect(jsonPath("$.data.items[0].coverFileId").value("9007199254740995"))
                    .andExpect(jsonPath("$.data.items[0].coverState").value("READY"))
                    .andExpect(jsonPath("$.data.items[0].cover").doesNotExist())
                    .andExpect(jsonPath("$.data.items[0].coverUrl").doesNotExist());
            mvc.perform(get("/home/sections/device"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.items[0].id").value("9007199254740997"))
                    .andExpect(jsonPath("$.data.items[0].fileId").value("9007199254740999"))
                    .andExpect(jsonPath("$.data.items[0].purchaseDate").value("2026-10-22"))
                    .andExpect(jsonPath("$.data.items[0].imageUrl").doesNotExist());
        }
    }

    private MockMvc mvc(HomeSectionService service) {
        var json = new JsonConfig().jacksonObjectMapper(new Jackson2ObjectMapperBuilder());
        return MockMvcBuilders.standaloneSetup(new HomeSectionController(service))
                .setControllerAdvice(new ExceptionHandle())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(json)).build();
    }

}
