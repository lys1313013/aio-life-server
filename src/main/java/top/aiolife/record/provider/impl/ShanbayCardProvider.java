package top.aiolife.record.provider.impl;

import cn.hutool.http.HttpUtil;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 扇贝单词卡片提供者
 *
 * @author Lys
 * @date 2026/02/18
 */
@Slf4j
@Component
@AllArgsConstructor
public class ShanbayCardProvider implements DashboardCardProvider {

    private final IUserBindService userBindService;

    @Override
    public String getType() {
        return "SHANBAY";
    }

    @Override
    public String getTitle() {
        return "扇贝单词";
    }

    @Override
    public String getTotalTitle() {
        return "今日时长";
    }

    @Override
    public String getIcon() {
        return "svg:shanbay";
    }

    @Override
    public int getOrder() {
        return 5;
    }

    @Override
    public boolean isVisible(long userId) {
        UserBindEntity bind = userBindService.getBindByUserIdAndPlatform(userId, "shanbay");
        return bind != null && bind.getPlatformUsername() != null;
    }

    @Override
    public DashboardCardVO getCard(long userId) {
        UserBindEntity bind = userBindService.getBindByUserIdAndPlatform(userId, "shanbay");
        if (bind == null || bind.getPlatformUsername() == null) {
            return null;
        }

        String username = bind.getPlatformUsername();
        DashboardCardVO card = new DashboardCardVO();
        card.setType(getType());
        card.setIcon(getIcon());
        card.setIconClickUrl("https://web.shanbay.com/web/users/" + username + "/checkin");
        card.setTitle(getTitle());
        card.setTitleClickUrl("https://web.shanbay.com/web/users/" + username + "/checkin");
        card.setTotalTitle(getTotalTitle());

        try {
            // 获取扇贝打卡数据
            String url = String.format("https://www.shanbay.com/api/v1/checkin/user/%s/?page=1&ipp=1", username);
            String jsonStr = HttpUtil.get(url);
            JSONObject res = JSON.parseObject(jsonStr);

            if (res != null && res.containsKey("data")) {
                JSONArray data = res.getJSONArray("data");
                if (data != null && !data.isEmpty()) {
                    JSONObject latestCheckin = data.getJSONObject(0);
                    
                    String checkinDateStr = latestCheckin.getString("checkin_date");
                    LocalDate checkinDate = LocalDate.parse(checkinDateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                    LocalDate today = LocalDate.now();
                    
                    boolean hasCheckedIn = today.equals(checkinDate);
                    
                    // 计算今日学习时间
                    double totalUsedTime = 0;
                    if (hasCheckedIn && latestCheckin.containsKey("stats")) {
                        JSONObject stats = latestCheckin.getJSONObject("stats");
                        for (String key : stats.keySet()) {
                            JSONObject item = stats.getJSONObject(key);
                            if (item != null && item.containsKey("used_time")) {
                                totalUsedTime += item.getDoubleValue("used_time");
                            }
                        }
                    }

                    // 格式化时间，保留0位小数
                    String totalValue = new BigDecimal(totalUsedTime).setScale(0, RoundingMode.HALF_UP).toString();
                    card.setTotalValue(totalValue + " 分钟");
                    
                    card.setValue(hasCheckedIn ? "已打卡" : "未打卡");
                    card.setValueColor(hasCheckedIn ? "#3fb27f" : "#ff4d4f");
                } else {
                    // 没有打卡记录
                    card.setTotalValue("0 分钟");
                    card.setValue("未打卡");
                    card.setValueColor("#ff4d4f");
                }
            } else {
                card.setValue("获取失败");
                card.setValueColor("#ff4d4f");
                log.error("Shanbay API response invalid: {}", jsonStr);
            }
        } catch (Exception e) {
            log.error("获取扇贝数据失败", e);
            card.setValue("获取失败");
            card.setValueColor("#ff4d4f");
            card.setTotalValue("0 分钟");
        }

        return card;
    }
}
