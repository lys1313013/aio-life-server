package top.aiolife.record.provider.impl;

import com.alibaba.fastjson2.JSONObject;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.aiolife.record.client.WeReadClient;
import top.aiolife.record.pojo.entity.UserBindEntity;
import top.aiolife.record.pojo.vo.DashboardCardVO;
import top.aiolife.record.provider.DashboardCardProvider;
import top.aiolife.record.service.IUserBindService;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

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

    private final IUserBindService userBindService;
    private final WeReadClient weReadClient;

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
        return "本月阅读";
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
            long todaySeconds = getTodayReadSeconds(data, LocalDate.now(WE_READ_ZONE_ID), WE_READ_ZONE_ID);
            long monthSeconds = data.getLongValue("totalReadTime");
            card.setValue(formatDuration(todaySeconds));
            card.setValueColor(todaySeconds == 0 ? "red" : "#3FB27F");
            card.setTotalValue(formatDuration(monthSeconds));

            card.setRefreshInterval(300);
        } catch (Exception e) {
            log.warn("获取用户微信读书数据失败，userId={}", userId, e);
            card.setValue("获取失败");
            card.setTotalValue("获取失败");
        }
        return card;
    }

    static long getTodayReadSeconds(JSONObject data, LocalDate today, ZoneId zoneId) {
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
                        return today.equals(bucketDate);
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
