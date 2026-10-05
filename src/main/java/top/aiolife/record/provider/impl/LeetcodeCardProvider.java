package top.aiolife.record.provider.impl;

import top.aiolife.record.pojo.entity.UserBindEntity;
import top.aiolife.record.pojo.vo.DashboardCardVO;
import top.aiolife.record.provider.DashboardCardProvider;
import top.aiolife.record.service.ILeetcodeService;
import top.aiolife.record.service.IUserBindService;
import top.aiolife.sso.mapper.UserMapper;
import top.aiolife.sso.pojo.entity.UserEntity;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Component;

/**
 * LeetCode 卡片提供者
 *
 * @author Lys
 * @date 2026/01/23 23:08
 */
@Slf4j
@Component
@AllArgsConstructor
public class LeetcodeCardProvider implements DashboardCardProvider {

    private final ILeetcodeService leetcodeService;
    private final UserMapper userMapper;
    private final IUserBindService userBindService;

    @Override
    public String getType() {
        return "LEETCODE";
    }

    @Override
    public String getTitle() {
        return "每日一题";
    }

    @Override
    public String getTotalTitle() {
        return "今日提交";
    }

    @Override
    public int getOrder() {
        return 2;
    }

    @Override
    public boolean isVisible(long userId) {
        UserEntity user = userMapper.selectById(userId);
        UserBindEntity bind = userBindService.getBindByUserIdAndPlatform(userId, "leetcode");
        return user != null && bind != null && bind.getPlatformUsername() != null;
    }

    @Override
    public DashboardCardVO getCard(long userId) {
        UserEntity user = userMapper.selectById(userId);
        UserBindEntity bind = userBindService.getBindByUserIdAndPlatform(userId, "leetcode");

        if (user == null || bind == null || bind.getPlatformUsername() == null) {
            return null;
        }

        DashboardCardVO card = new DashboardCardVO();
        card.setType(getType());
        card.setIconClickUrl("https://leetcode.cn/u/" + bind.getPlatformUsername());
        card.setTitle(getTitle());
        try {
            Pair<Boolean, String> result = leetcodeService.checkToday(user, false);
            card.setTitleClickUrl(result.getSecond());
            card.setValue(result.getFirst() ? "已完成" : "未完成");
            card.setValueColor(result.getFirst() ? "#3FB27F" : "red");
            card.setTotalTitle(getTotalTitle());
            card.setTotalValue(String.valueOf(leetcodeService.getTodaySubmissionCount(bind.getPlatformUsername())));
            card.setRefreshInterval(600);
        } catch (Exception e) {
            log.error("获取 LeetCode 数据失败", e);
            card.setValue("获取失败");
        }
        return card;
    }
}
