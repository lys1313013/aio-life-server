package top.aiolife.record.provider.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import top.aiolife.record.client.WeReadClient;
import top.aiolife.record.pojo.entity.UserBindEntity;
import top.aiolife.record.pojo.vo.DashboardCardVO;
import top.aiolife.record.service.IUserBindService;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReadCardProviderTest {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Mock
    private IUserBindService userBindService;

    @Mock
    private WeReadClient weReadClient;

    @InjectMocks
    private ReadCardProvider provider;

    @Test
    void isVisible_仅在配置ApiKey后展示() {
        when(userBindService.getBindByUserIdAndPlatform(1L, "weread"))
                .thenReturn(UserBindEntity.builder().accessToken("wrk-test").build());
        when(userBindService.getBindByUserIdAndPlatform(2L, "weread"))
                .thenReturn(UserBindEntity.builder().accessToken(" ").build());

        assertTrue(provider.isVisible(1L));
        assertFalse(provider.isVisible(2L));
    }

    @Test
    void getCard_使用微信读书月度统计生成卡片() {
        UserBindEntity bind = UserBindEntity.builder().accessToken("wrk-test").build();
        when(userBindService.getBindByUserIdAndPlatform(1L, "weread")).thenReturn(bind);

        long todayBucket = LocalDate.now(SHANGHAI).atStartOfDay(SHANGHAI).toEpochSecond();
        JSONObject data = JSON.parseObject("{\"totalReadTime\":7380,\"readTimes\":{\""
                + todayBucket + "\":1380}}");
        when(weReadClient.getCurrentMonthReadData("wrk-test")).thenReturn(data);

        DashboardCardVO card = provider.getCard(1L);

        assertEquals("23分", card.getValue());
        assertEquals("2小时3分", card.getTotalValue());
        assertEquals("本月阅读", card.getTotalTitle());
    }

    @Test
    void formatDuration_按秒转换为简洁时长() {
        assertEquals("0分", ReadCardProvider.formatDuration(0));
        assertEquals("<1分", ReadCardProvider.formatDuration(59));
        assertEquals("1分", ReadCardProvider.formatDuration(60));
        assertEquals("1小时1分", ReadCardProvider.formatDuration(3660));
    }
}
