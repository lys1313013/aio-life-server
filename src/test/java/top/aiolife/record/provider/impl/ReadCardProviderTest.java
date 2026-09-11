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
import top.aiolife.record.util.RedisUtil;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReadCardProviderTest {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Mock
    private IUserBindService userBindService;

    @Mock
    private WeReadClient weReadClient;

    @Mock
    private RedisUtil redisUtil;

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
    void getCard_今日已阅读时在昨日连续天数上加一() {
        UserBindEntity bind = UserBindEntity.builder().accessToken("wrk-test").build();
        when(userBindService.getBindByUserIdAndPlatform(1L, "weread")).thenReturn(bind);

        LocalDate today = LocalDate.now(SHANGHAI);
        long todayBucket = today.atStartOfDay(SHANGHAI).toEpochSecond();
        JSONObject data = JSON.parseObject("{\"totalReadTime\":7380,\"readTimes\":{\""
                + todayBucket + "\":1380}}");
        when(weReadClient.getCurrentMonthReadData("wrk-test")).thenReturn(data);
        when(redisUtil.get(ReadCardProvider.streakCacheKey(1L, "wrk-test", today.minusDays(1))))
                .thenReturn("4");

        DashboardCardVO card = provider.getCard(1L);

        assertEquals("23分", card.getValue());
        assertEquals("5 天", card.getTotalValue());
        assertEquals("连续阅读", card.getTotalTitle());
        verify(redisUtil).set(
                ReadCardProvider.streakCacheKey(1L, "wrk-test", today),
                "5",
                72,
                TimeUnit.HOURS);
    }

    @Test
    void getCard_今日未阅读时保留昨日连续天数并缓存今日为零() {
        UserBindEntity bind = UserBindEntity.builder().accessToken("wrk-test").build();
        when(userBindService.getBindByUserIdAndPlatform(1L, "weread")).thenReturn(bind);

        LocalDate today = LocalDate.now(SHANGHAI);
        when(weReadClient.getCurrentMonthReadData("wrk-test"))
                .thenReturn(JSON.parseObject("{\"totalReadTime\":3600,\"readTimes\":{}}"));
        when(redisUtil.get(ReadCardProvider.streakCacheKey(1L, "wrk-test", today.minusDays(1))))
                .thenReturn("4");

        DashboardCardVO card = provider.getCard(1L);

        assertEquals("0分", card.getValue());
        assertEquals("4 天", card.getTotalValue());
        verify(redisUtil).set(
                ReadCardProvider.streakCacheKey(1L, "wrk-test", today),
                "0",
                72,
                TimeUnit.HOURS);
    }

    @Test
    void getStreakEndingOn_缓存缺失时从每日明细向前计算() {
        LocalDate currentDate = LocalDate.of(2026, 9, 11);
        JSONObject readTimes = new JSONObject();
        readTimes.put(String.valueOf(LocalDate.of(2026, 9, 10)
                .atStartOfDay(SHANGHAI).toEpochSecond()), 600);
        readTimes.put(String.valueOf(LocalDate.of(2026, 9, 9)
                .atStartOfDay(SHANGHAI).toEpochSecond()), 60);
        readTimes.put(String.valueOf(LocalDate.of(2026, 9, 8)
                .atStartOfDay(SHANGHAI).toEpochSecond()), 59);
        JSONObject data = new JSONObject();
        data.put("readTimes", readTimes);

        int streak = provider.getStreakEndingOn(
                1L, "wrk-test", currentDate.minusDays(1), currentDate, data);

        assertEquals(2, streak);
        verify(redisUtil).set(
                ReadCardProvider.streakCacheKey(1L, "wrk-test", currentDate.minusDays(1)),
                "2",
                72,
                TimeUnit.HOURS);
    }

    @Test
    void getStreakEndingOn_跨月时按需查询上月() {
        LocalDate currentDate = LocalDate.of(2026, 9, 1);
        LocalDate targetDate = currentDate.minusDays(1);
        long previousMonthBaseTime = targetDate.withDayOfMonth(1)
                .atStartOfDay(SHANGHAI)
                .toEpochSecond();
        JSONObject previousMonthTimes = new JSONObject();
        previousMonthTimes.put(String.valueOf(targetDate.atStartOfDay(SHANGHAI).toEpochSecond()), 60);
        JSONObject previousMonthData = new JSONObject();
        previousMonthData.put("readTimes", previousMonthTimes);
        when(weReadClient.getMonthlyReadData("wrk-test", previousMonthBaseTime))
                .thenReturn(previousMonthData);

        int streak = provider.getStreakEndingOn(
                1L, "wrk-test", targetDate, currentDate, new JSONObject());

        assertEquals(1, streak);
        verify(weReadClient).getMonthlyReadData("wrk-test", previousMonthBaseTime);
    }

    @Test
    void formatDuration_按秒转换为简洁时长() {
        assertEquals("0分", ReadCardProvider.formatDuration(0));
        assertEquals("<1分", ReadCardProvider.formatDuration(59));
        assertEquals("1分", ReadCardProvider.formatDuration(60));
        assertEquals("1小时1分", ReadCardProvider.formatDuration(3660));
    }
}
