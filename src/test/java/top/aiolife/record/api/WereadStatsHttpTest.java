package top.aiolife.record.api;

import cn.dev33.satoken.stp.StpUtil;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import top.aiolife.core.web.CurrentUserIdArgumentResolver;
import top.aiolife.config.JsonConfig;
import top.aiolife.core.exception.ExceptionHandle;
import top.aiolife.core.query.QueryParamsArgumentResolver;
import top.aiolife.record.pojo.vo.WereadStatsVO;
import top.aiolife.record.service.HomeSectionService;
import top.aiolife.record.service.WereadStatsService;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WereadStatsHttpTest {
    private final HomeSectionService sections = mock(HomeSectionService.class);
    private final WereadStatsService stats = mock(WereadStatsService.class);

    @Test
    void 阅读统计按登录态查询并返回聚合字段() throws Exception {
        when(stats.stats(7L, "week")).thenReturn(new WereadStatsVO(true, "week",
                "2026-11-02", "2026-11-08", 5,
                List.of(new WereadStatsVO.Bucket("2026-11-02", "周一", 2100)),
                2100, 420, "2026-10-26", "2026-10-30", 1500, 600));
        try (var login = mockStatic(StpUtil.class)) {
            login.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            login.when(StpUtil::getLoginIdAsString).thenReturn("7");
            mvc().perform(get("/home/sections/weread-stats").param("granularity", "week"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.connected").value(true))
                    .andExpect(jsonPath("$.data.granularity").value("week"))
                    .andExpect(jsonPath("$.data.periodStart").value("2026-11-02"))
                    .andExpect(jsonPath("$.data.elapsedDays").value(5))
                    .andExpect(jsonPath("$.data.buckets[0].key").value("2026-11-02"))
                    .andExpect(jsonPath("$.data.buckets[0].label").value("周一"))
                    .andExpect(jsonPath("$.data.buckets[0].seconds").value(2100))
                    .andExpect(jsonPath("$.data.totalSeconds").value(2100))
                    .andExpect(jsonPath("$.data.dailyAverageSeconds").value(420))
                    .andExpect(jsonPath("$.data.previousTotalSeconds").value(1500))
                    .andExpect(jsonPath("$.data.refreshInterval").value(600));
            verify(stats).stats(7L, "week");
        }
    }

    @Test
    void 阅读统计拒绝缺失或非法粒度() throws Exception {
        try (var login = mockStatic(StpUtil.class)) {
            login.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            login.when(StpUtil::getLoginIdAsString).thenReturn("7");
            mvc().perform(get("/home/sections/weread-stats"))
                    .andExpect(status().isBadRequest());
            mvc().perform(get("/home/sections/weread-stats").param("granularity", "overall"))
                    .andExpect(status().isBadRequest());
            verifyNoInteractions(stats);
        }
    }

    private MockMvc mvc() {
        var json = new JsonConfig().jacksonObjectMapper(new Jackson2ObjectMapperBuilder());
        return MockMvcBuilders.standaloneSetup(new HomeSectionController(sections, stats))
                .setControllerAdvice(new ExceptionHandle())
                .setCustomArgumentResolvers(new CurrentUserIdArgumentResolver(), new QueryParamsArgumentResolver(json))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(json)).build();
    }
}
