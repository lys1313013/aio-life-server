package top.aiolife.record.provider.impl;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.aiolife.record.pojo.entity.UserBindEntity;
import top.aiolife.record.pojo.vo.DashboardCardVO;
import top.aiolife.record.provider.DashboardCardProvider;
import top.aiolife.record.service.IUserBindService;
import top.aiolife.record.util.RedisUtil;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * GitHub 卡片提供者
 *
 * @author Lys
 * @date 2026/02/17
 */
@Slf4j
@Component
@AllArgsConstructor
public class GithubCardProvider implements DashboardCardProvider {

    private final IUserBindService userBindService;

    private final RedisUtil redisUtil;

    /**
     * 「是否展示 GitHub 卡片」决策的 Redis 键前缀（按用户隔离），绑定变更时由 UserBindController 失效
     */
    private static final String VISIBLE_KEY_PREFIX = "github:visible:";

    /**
     * 决策缓存时长。绑定变更时会被主动失效，此 TTL 仅作为兜底
     */
    private static final long VISIBLE_CACHE_MINUTES = 10;

    @Override
    public String getType() {
        return "GITHUB";
    }

    @Override
    public String getTitle() {
        return "GitHub";
    }

    @Override
    public String getTotalTitle() {
        return "连续提交";
    }

    @Override
    public String getIcon() {
        return "mdi:github";
    }

    @Override
    public int getOrder() {
        return 3;
    }

    @Override
    public boolean isVisible(long userId) {
        return isCardVisible(userId);
    }

    /**
     * 判断该用户是否展示 GitHub 卡片（绑定有效：有用户名 + 有 Token）。
     * 决策结果走 Redis 缓存，未命中回源数据库并回写，避免每次 /dashboard/tasks 都查库。
     *
     * @param userId 用户 ID
     * @return true 表示应展示
     */
    private boolean isCardVisible(long userId) {
        String key = VISIBLE_KEY_PREFIX + userId;
        String cached = redisUtil.get(key);
        if (cached != null) {
            return Boolean.parseBoolean(cached);
        }
        UserBindEntity bind = userBindService.getBindByUserIdAndPlatform(userId, "github");
        boolean visible = bind != null
                && bind.getPlatformUsername() != null && !bind.getPlatformUsername().isEmpty()
                && bind.getAccessToken() != null && !bind.getAccessToken().isEmpty();
        redisUtil.set(key, String.valueOf(visible), VISIBLE_CACHE_MINUTES, TimeUnit.MINUTES);
        return visible;
    }

    @Override
    public DashboardCardVO getCard(long userId) {
        if (!isCardVisible(userId)) {
            // 未绑定：直接短路，不发起慢的 GitHub 请求
            return null;
        }
        UserBindEntity bind = userBindService.getBindByUserIdAndPlatform(userId, "github");
        if (bind == null || bind.getPlatformUsername() == null || bind.getAccessToken() == null) {
            return null;
        }

        DashboardCardVO card = new DashboardCardVO();
        card.setType(getType());
        card.setIcon(getIcon());
        card.setIconClickUrl("https://github.com/" + bind.getPlatformUsername());
        card.setTitle(getTitle());
        card.setTotalTitle(getTotalTitle());

        try {
            JSONObject data = fetchGithubData(bind.getPlatformUsername(), bind.getAccessToken());
            if (data != null && data.containsKey("data")) {
                JSONObject contributionCalendar = data.getJSONObject("data")
                        .getJSONObject("user")
                        .getJSONObject("contributionsCollection")
                        .getJSONObject("contributionCalendar");

                // 计算连续提交天数
                int streak = calculateCurrentStreak(contributionCalendar);
                card.setTotalValue(streak + " 天");

                // 计算今日提交数
                int todayContributions = getTodayContributions(contributionCalendar);
                
                // GitHub 的 contributionCalendar 存在缓存延迟。如果日历显示今天为0，尝试通过 Events API 获取实时数据补偿
                if (todayContributions == 0) {
                    int realTimeContributions = getRealTimeTodayContributions(bind.getPlatformUsername(), bind.getAccessToken());
                    if (realTimeContributions > 0) {
                        todayContributions = realTimeContributions;
                    }
                }

                card.setValue(todayContributions + "");
                card.setValueColor(todayContributions > 0 ? "#3FB27F" : "red");
                card.setTitleClickUrl("https://github.com/" + bind.getPlatformUsername());
            } else {
                card.setValue("获取失败");
                log.error("GitHub API response invalid: {}", data);
            }
        } catch (Exception e) {
            log.error("获取 GitHub 数据失败", e);
            card.setValue("获取失败");
        }
        
        return card;
    }

    /**
     * 获取 GitHub 数据
     *
     * @param username GitHub 用户名
     * @param token    GitHub Token
     * @return GitHub 数据
     */
    private JSONObject fetchGithubData(String username, String token) {
        String query = "{ \"query\": \"query { user(login: \\\"" + username + "\\\") { contributionsCollection { contributionCalendar { totalContributions weeks { contributionDays { contributionCount date } } } } } }\" }";
        
        try (HttpResponse response = HttpRequest.post("https://api.github.com/graphql")
                .header("Authorization", "Bearer " + token)
                .body(query)
                .execute()) {
            if (response.isOk()) {
                return JSON.parseObject(response.body());
            } else {
                log.error("GitHub API error: status={}, body={}", response.getStatus(), response.body());
            }
        }
        return null;
    }

    /**
     * 通过 Events API 获取今日实时的活动数，用于补偿 contributionCalendar 的缓存延迟
     */
    private int getRealTimeTodayContributions(String username, String token) {
        try (HttpResponse response = HttpRequest.get("https://api.github.com/users/" + username + "/events/public")
                .header("Authorization", "Bearer " + token)
                .execute()) {
            if (response.isOk()) {
                JSONArray events = JSON.parseArray(response.body());
                int count = 0;
                LocalDate today = LocalDate.now();
                for (int i = 0; i < events.size(); i++) {
                    JSONObject event = events.getJSONObject(i);
                    String createdAt = event.getString("created_at");
                    if (createdAt != null) {
                        LocalDate eventDate = Instant.parse(createdAt).atZone(ZoneId.systemDefault()).toLocalDate();
                        if (today.equals(eventDate)) {
                            String type = event.getString("type");
                            // 常见的计入 contribution 的事件类型
                            if ("PushEvent".equals(type) || "IssuesEvent".equals(type) || "PullRequestEvent".equals(type) || "CreateEvent".equals(type)) {
                                // 这里简化处理：只要有相关事件，就累加
                                // 如果是 PushEvent，原本应按 commits 数量，但为了简单补偿直接按事件数或简单提取 size
                                if ("PushEvent".equals(type)) {
                                    JSONObject payload = event.getJSONObject("payload");
                                    if (payload != null && payload.containsKey("size")) {
                                        count += payload.getIntValue("size");
                                    } else {
                                        count++;
                                    }
                                } else {
                                    count++;
                                }
                            }
                        } else if (eventDate.isBefore(today)) {
                            // 因为 events 是按时间倒序排列的，一旦遇到早于今天的事件，就可以提前结束循环
                            break;
                        }
                    }
                }
                return count;
            } else {
                log.error("GitHub Events API error: status={}, body={}", response.getStatus(), response.body());
            }
        } catch (Exception e) {
            log.error("获取 GitHub Events 实时数据失败", e);
        }
        return 0;
    }

    /**
     * 计算当前连续提交天数

     *
     * @param calendar 贡献日历
     * @return 连续提交天数
     */
    private int calculateCurrentStreak(JSONObject calendar) {
        JSONArray weeks = calendar.getJSONArray("weeks");
        List<JSONObject> allDays = new ArrayList<>();
        
        for (int i = 0; i < weeks.size(); i++) {
            JSONArray days = weeks.getJSONObject(i).getJSONArray("contributionDays");
            for (int j = 0; j < days.size(); j++) {
                allDays.add(days.getJSONObject(j));
            }
        }
        
        // 此时数据按日期升序排列（weeks -> days），我们将倒序遍历
        
        if (allDays.isEmpty()) {
            return 0;
        }

        int streak = 0;

        // 从最后一天（今天）倒序检查
        for (int i = allDays.size() - 1; i >= 0; i--) {
            JSONObject day = allDays.get(i);
            int count = day.getIntValue("contributionCount");
            
            if (count > 0) {
                streak++;
            } else {
                // 如果是最后记录的一天（根据时区可能被视为“今天”或“昨天”）且提交数为 0，
                // 我们允许跳过它（不中断连续记录，也不增加计数）。
                // 通常逻辑：
                // - 如果今天有提交：连续天数包含今天。
                // - 如果今天无提交：连续天数维持昨天的状态。
                // - 如果昨天无提交：连续天数归零。
                
                // 仅处理最后一天为 0 的情况
                if (i == allDays.size() - 1) {
                    continue; 
                } else {
                    break; 
                }
            }
        }
        
        return streak;
    }

    /**
     * 获取今日提交数
     *
     * @param calendar 贡献日历
     * @return 今日提交数
     */
    private int getTodayContributions(JSONObject calendar) {
        JSONArray weeks = calendar.getJSONArray("weeks");
        JSONObject lastDay = null;

        for (int i = 0; i < weeks.size(); i++) {
            JSONArray days = weeks.getJSONObject(i).getJSONArray("contributionDays");
            if (!days.isEmpty()) {
                lastDay = days.getJSONObject(days.size() - 1);
            }
        }
        
        // 始终使用 GitHub 返回的最后一天作为“今天”
        // 处理时区差异和系统时钟不匹配的问题（例如系统是 2026，GitHub 是 2025）
        if (lastDay != null) {
            return lastDay.getIntValue("contributionCount");
        }
        
        return 0;
    }
}
