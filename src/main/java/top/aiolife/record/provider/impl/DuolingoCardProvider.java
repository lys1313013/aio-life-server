package top.aiolife.record.provider.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;
import top.aiolife.record.client.DuolingoClient;
import top.aiolife.record.pojo.vo.DashboardCardVO;
import top.aiolife.record.provider.DashboardCardProvider;
import top.aiolife.record.service.IUserThirdPartyAccountService;

/** 多邻国公开账号概览。 */
@Component
@RequiredArgsConstructor
public class DuolingoCardProvider implements DashboardCardProvider {
    private final IUserThirdPartyAccountService accounts;
    private final DuolingoClient client;

    @Override
    public String getType() {
        return "DUOLINGO";
    }

    @Override
    public String getTitle() {
        return "多邻国";
    }

    @Override
    public String getTotalTitle() {
        return "连续打卡";
    }

    @Override
    public int getOrder() {
        return 7;
    }

    private String boundUsername(long userId) {
        var bind = accounts.getBindByUserIdAndPlatform(userId, "duolingo");
        return bind == null ? null : bind.getPlatformUsername();
    }

    @Override
    public boolean isVisible(long userId) {
        return StringUtils.hasText(boundUsername(userId));
    }

    @Override
    public DashboardCardVO getCard(long userId) {
        String username = boundUsername(userId);
        if (!StringUtils.hasText(username)) {
            return null;
        }
        var streak = client.getStreak(username);
        var profile = UriComponentsBuilder.fromUriString("https://www.duolingo.com/profile")
                .pathSegment("{username}").encode().buildAndExpand(username).toUriString();
        var card = new DashboardCardVO();
        card.setType(getType());
        card.setTitle(getTitle());
        card.setTitleClickUrl(profile);
        card.setIconClickUrl(profile);
        card.setValue(username);
        card.setTotalTitle(getTotalTitle());
        card.setTotalValue(streak.isPresent() ? streak.getAsInt() + " 天" : "未找到公开账号");
        return card;
    }
}
