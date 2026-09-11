package top.aiolife.record.provider.impl;

import cn.hutool.crypto.digest.DigestUtil;
import com.alibaba.fastjson2.JSONObject;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.aiolife.record.client.WeReadClient;
import top.aiolife.record.pojo.entity.UserBindEntity;
import top.aiolife.record.pojo.vo.DashboardCardVO;
import top.aiolife.record.provider.DashboardCardProvider;
import top.aiolife.record.service.IUserBindService;
import top.aiolife.record.util.RedisUtil;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.YearMonth;
import java.util.concurrent.TimeUnit;

/**
 * 阅读卡片提供者
 *
 * @author Lys
 * @date 2026/07/08
 */
@Slf4j
@Component
@AllArgsConstructor
public class ReadCardProvider implements DashboardCardProvider {

    private static final String PLATFORM = "weread";
    private static final ZoneId WE_READ_ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final long EFFECTIVE_READ_SECONDS = 60;
    private static final String STREAK_CACHE_KEY_PREFIX = "weread:streak:";
    private static final long STREAK_CACHE_HOURS = 72;
    private static final int MAX_HISTORY_MONTHS = 24;

    private final IUserBindService userBindService;
    private final WeReadClient weReadClient;
    private final RedisUtil redisUtil;

    @Override
    public String getType() {
        return "READ";
    }

    @Override
    public String getTitle() {
        return "今日阅读";
    }

    @Override
    public String getTotalTitle() {
        return "连续阅读";
    }

    @Override
    public String getIcon() {
        return "lucide:book-open";
    }

    @Override
    public int getOrder() {
        return 6;
    }

    @Override
    public boolean isVisible(long userId) {
        UserBindEntity bind = userBindService.getBindByUserIdAndPlatform(userId, PLATFORM);
        return bind != null && bind.getAccessToken() != null && !bind.getAccessToken().isBlank();
    }

    @Override
    public DashboardCardVO getCard(long userId) {
        UserBindEntity bind = userBindService.getBindByUserIdAndPlatform(userId, PLATFORM);
        if (bind == null || bind.getAccessToken() == null || bind.getAccessToken().isBlank()) {
            return null;
        }

        DashboardCardVO card = new DashboardCardVO();
        card.setType(getType());
        card.setIcon(getIcon());
        card.setTitle(getTitle());
        card.setTitleClickUrl("https://weread.qq.com/");
        card.setTotalTitle(getTotalTitle());

        try {
            JSONObject data = weReadClient.getCurrentMonthReadData(bind.getAccessToken());
            LocalDate today = LocalDate.now(WE_READ_ZONE_ID);
            long todaySeconds = getReadSeconds(data, today, WE_READ_ZONE_ID);
            int previousStreak = getStreakEndingOn(
                    userId, bind.getAccessToken(), today.minusDays(1), today, data);
            boolean readToday = todaySeconds >= EFFECTIVE_READ_SECONDS;
            int todayStreak = readToday ? previousStreak + 1 : 0;
            int displayedStreak = readToday ? todayStreak : previousStreak;

            cacheStreak(userId, bind.getAccessToken(), today, todayStreak);
            card.setValue(formatDuration(todaySeconds));
            card.setValueColor(todaySeconds == 0 ? "red" : "#3FB27F");
            card.setTotalValue(displayedStreak + " 天");

            card.setRefreshInterval(300);
        } catch (Exception e) {
            log.warn("获取用户微信读书数据失败，userId={}", userId, e);
            card.setValue("获取失败");
            card.setTotalValue("获取失败");
        }
        return card;
    }

    int getStreakEndingOn(
            long userId,
            String apiKey,
            LocalDate targetDate,
            LocalDate currentDate,
            JSONObject currentMonthData) {
        Integer cached = getCachedStreak(userId, apiKey, targetDate);
        if (cached != null) {
            return cached;
        }

        int streak = 0;
        int historicalRequests = 0;
        LocalDate cursor = targetDate;
        YearMonth loadedMonth = YearMonth.from(currentDate);
        JSONObject loadedData = currentMonthData;

        while (historicalRequests <= MAX_HISTORY_MONTHS) {
            if (!cursor.equals(targetDate)) {
                Integer olderCachedStreak = getCachedStreak(userId, apiKey, cursor);
                if (olderCachedStreak != null) {
                    streak += olderCachedStreak;
                    cacheStreak(userId, apiKey, targetDate, streak);
                    return streak;
                }
            }

            YearMonth cursorMonth = YearMonth.from(cursor);
            if (!cursorMonth.equals(loadedMonth)) {
                if (historicalRequests == MAX_HISTORY_MONTHS) {
                    log.warn("微信读书连续阅读回溯达到上限，userId={}, months={}",
                            userId, MAX_HISTORY_MONTHS);
                    break;
                }
                long baseTime = cursor.withDayOfMonth(1)
                        .atStartOfDay(WE_READ_ZONE_ID)
                        .toEpochSecond();
                loadedData = weReadClient.getMonthlyReadData(apiKey, baseTime);
                loadedMonth = cursorMonth;
                historicalRequests++;
            }

            while (YearMonth.from(cursor).equals(loadedMonth)) {
                if (getReadSeconds(loadedData, cursor, WE_READ_ZONE_ID) < EFFECTIVE_READ_SECONDS) {
                    cacheStreak(userId, apiKey, targetDate, streak);
                    return streak;
                }
                streak++;
                cursor = cursor.minusDays(1);
            }
        }

        cacheStreak(userId, apiKey, targetDate, streak);
        return streak;
    }

    private Integer getCachedStreak(long userId, String apiKey, LocalDate date) {
        try {
            String value = redisUtil.get(streakCacheKey(userId, apiKey, date));
            return value == null ? null : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            log.warn("微信读书连续阅读缓存格式无效，userId={}, date={}", userId, date);
            return null;
        } catch (Exception e) {
            log.warn("读取微信读书连续阅读缓存失败，userId={}, date={}", userId, date, e);
            return null;
        }
    }

    private void cacheStreak(long userId, String apiKey, LocalDate date, int streak) {
        try {
            redisUtil.set(
                    streakCacheKey(userId, apiKey, date),
                    String.valueOf(streak),
                    STREAK_CACHE_HOURS,
                    TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("写入微信读书连续阅读缓存失败，userId={}, date={}", userId, date, e);
        }
    }

    static String streakCacheKey(long userId, String apiKey, LocalDate date) {
        String apiKeyFingerprint = DigestUtil.sha256Hex(apiKey.trim()).substring(0, 12);
        return STREAK_CACHE_KEY_PREFIX + userId + ":" + apiKeyFingerprint + ":" + date;
    }

    static long getReadSeconds(JSONObject data, LocalDate date, ZoneId zoneId) {
        JSONObject readTimes = data.getJSONObject("readTimes");
        if (readTimes == null) {
            return 0;
        }
        return readTimes.keySet().stream()
                .filter(timestamp -> {
                    try {
                        LocalDate bucketDate = Instant.ofEpochSecond(Long.parseLong(timestamp))
                                .atZone(zoneId)
                                .toLocalDate();
                        return date.equals(bucketDate);
                    } catch (NumberFormatException e) {
                        return false;
                    }
                })
                .mapToLong(readTimes::getLongValue)
                .sum();
    }

    static String formatDuration(long seconds) {
        if (seconds <= 0) {
            return "0分";
        }
        if (seconds < 60) {
            return "<1分";
        }
        long minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + "分";
        }
        long hours = minutes / 60;
        long mins = minutes % 60;
        return mins == 0 ? hours + "小时" : hours + "小时" + mins + "分";
    }
}
