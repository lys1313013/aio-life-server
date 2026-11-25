package top.aiolife.record.provider.impl;

import org.junit.jupiter.api.Test;
import top.aiolife.record.client.DuolingoClient;
import top.aiolife.record.client.DuolingoClient.Streak;
import top.aiolife.record.pojo.entity.UserThirdPartyAccountEntity;
import top.aiolife.record.provider.impl.DuolingoCardProvider.CheckInStatus;
import top.aiolife.record.service.IUserThirdPartyAccountService;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DuolingoCardProviderTest {
    @Test
    void checkInStatus_区分今日打卡未打卡及未知() {
        var today = LocalDate.of(2026, 11, 25);
        assertEquals(CheckInStatus.CHECKED_IN, DuolingoCardProvider.checkInStatus(new Streak(3, today), today));
        assertEquals(CheckInStatus.NOT_CHECKED_IN,
                DuolingoCardProvider.checkInStatus(new Streak(3, today.minusDays(1)), today));
        assertEquals(CheckInStatus.NOT_CHECKED_IN, DuolingoCardProvider.checkInStatus(new Streak(0, null), today));
        assertEquals(CheckInStatus.UNKNOWN, DuolingoCardProvider.checkInStatus(new Streak(3, null), today));
        assertEquals(CheckInStatus.UNKNOWN, DuolingoCardProvider.checkInStatus(new Streak(3, today.plusDays(1)), today));
    }

    @Test
    void getCard_状态占用原用户名位置并保留主页链接() {
        var accounts = mock(IUserThirdPartyAccountService.class);
        var client = mock(DuolingoClient.class);
        var provider = new DuolingoCardProvider(accounts, client);
        when(accounts.getBindByUserIdAndPlatform(1L, "duolingo"))
                .thenReturn(UserThirdPartyAccountEntity.builder().platformUsername("example").build());
        when(client.getStreak("example")).thenReturn(Optional.of(new Streak(0, null)));

        var card = provider.getCard(1L);
        assertEquals("未打卡", card.getValue());
        assertEquals(DashboardCardColors.NEGATIVE, card.getValueColor());
        assertEquals("连续打卡", card.getTotalTitle());
        assertEquals("0 天", card.getTotalValue());
        assertEquals("https://www.duolingo.com/profile/example", card.getTitleClickUrl());
        assertEquals(card.getTitleClickUrl(), card.getIconClickUrl());

        when(client.getStreak("example")).thenReturn(Optional.of(new Streak(3, null)));
        var unknownCard = provider.getCard(1L);
        assertEquals("状态未知", unknownCard.getValue());
        assertEquals("", unknownCard.getValueColor());
        assertEquals("3 天", unknownCard.getTotalValue());
    }
}
