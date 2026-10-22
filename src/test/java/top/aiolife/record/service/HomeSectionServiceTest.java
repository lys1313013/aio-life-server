package top.aiolife.record.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.web.util.UriComponentsBuilder;
import top.aiolife.record.mapper.HomeSectionMapper;
import top.aiolife.record.pojo.entity.BVideoEntity;
import top.aiolife.record.pojo.entity.DeviceEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class HomeSectionServiceTest {
    private final HomeSectionMapper mapper = mock(HomeSectionMapper.class);
    private final HomeSectionAccessGuard access = mock(HomeSectionAccessGuard.class);
    private final HomeSectionService service = new HomeSectionService(mapper, access);
    private final LocalDate today = LocalDate.of(2026, 10, 22);

    @Test
    void testDevice_日期与日均费用使用相同天数并四舍五入() {
        var row = device(today.minusDays(3), "10");
        var result = HomeSectionService.device(row, today);
        assertEquals(3, result.getUsageDays());
        assertEquals(new BigDecimal("3.33"), result.getDailyCost());
        row.setEndDate(today.minusDays(1));
        result = HomeSectionService.device(row, today);
        assertEquals(2, result.getUsageDays());
        assertEquals(new BigDecimal("5.00"), result.getDailyCost());
        assertEquals("Laptop", result.getName());
        assertEquals(123L, result.getId());
    }

    @Test
    void testDevice_同日未来日期至少一天但未知购买日期和价格保持未知() {
        for (var purchase : List.of(today, today.plusDays(1))) {
            var result = HomeSectionService.device(device(purchase, "10"), today);
            assertEquals(1, result.getUsageDays());
            assertEquals(new BigDecimal("10.00"), result.getDailyCost());
        }
        var unknownDate = HomeSectionService.device(device(null, "10"), today);
        assertNull(unknownDate.getUsageDays());
        assertNull(unknownDate.getDailyCost());
        var unknownPrice = HomeSectionService.device(device(today.minusDays(3), null), today);
        assertEquals(3, unknownPrice.getUsageDays());
        assertNull(unknownPrice.getDailyCost());
    }

    private DeviceEntity device(LocalDate date, String price) {
        var row = new DeviceEntity();
        row.setId(123L);
        row.setName("Laptop");
        row.setPurchaseDate(date);
        row.setPurchasePrice(price == null ? null : new BigDecimal(price));
        return row;
    }

    @Test
    void testVideo_异常分集时长归一化且百分比不溢出() {
        var row = new BVideoEntity();
        row.setEpisodes(0);
        row.setCurrentEpisode(-1);
        row.setDuration(-1);
        row.setWatchedDuration(-1);
        var result = HomeSectionService.video(row);
        assertEquals(1, result.getEpisodes());
        assertEquals(1, result.getCurrentEpisode());
        assertEquals(0, result.getDuration());
        assertEquals(0, result.getWatchedDuration());
        assertEquals(0, result.getProgressPercent());
        row.setEpisodes(3);
        row.setCurrentEpisode(9);
        row.setDuration(10);
        row.setWatchedDuration(Integer.MAX_VALUE);
        result = HomeSectionService.video(row);
        assertEquals(3, result.getCurrentEpisode());
        assertEquals(100, result.getProgressPercent());
        row.setDuration(3);
        row.setWatchedDuration(1);
        assertEquals(33, HomeSectionService.video(row).getProgressPercent());
    }

    @Test
    void testVideo_安全续播地址保留同集时间跨集移除并不使用累计时长() {
        var row = new BVideoEntity();
        row.setEpisodes(5);
        row.setCurrentEpisode(3);
        row.setWatchedDuration(1200);
        row.setUrl("https://www.bilibili.com/video/BV123?p=2&t=60&foo=bar");
        var crossEpisode = UriComponentsBuilder.fromUriString(HomeSectionService.video(row).getResumeUrl()).build();
        assertEquals("www.bilibili.com", crossEpisode.getHost());
        assertEquals("/video/BV123", crossEpisode.getPath());
        assertEquals(List.of("3"), crossEpisode.getQueryParams().get("p"));
        assertEquals("bar", crossEpisode.getQueryParams().getFirst("foo"));
        assertNull(crossEpisode.getQueryParams().get("t"));
        row.setUrl("https://www.bilibili.com/video/BV123?p=3&t=60");
        var sameEpisode = UriComponentsBuilder.fromUriString(HomeSectionService.video(row).getResumeUrl()).build();
        assertEquals(List.of("3"), sameEpisode.getQueryParams().get("p"));
        assertEquals(List.of("60"), sameEpisode.getQueryParams().get("t"));
        row.setUrl("http://www.bilibili.com/video/BV123?p=3&t=60");
        var secure = UriComponentsBuilder.fromUriString(HomeSectionService.video(row).getResumeUrl()).build();
        assertEquals("https", secure.getScheme());
        assertEquals("60", secure.getQueryParams().getFirst("t"));
        row.setUrl(null);
        row.setBvid("BV123");
        assertEquals("https://www.bilibili.com/video/BV123?p=3", HomeSectionService.video(row).getResumeUrl());
    }

    @Test
    void testVideo_拒绝伪造域名脚本地址和非法BV号() {
        var row = new BVideoEntity();
        for (var url : List.of("javascript:alert(1)", "https://bilibili.com.attacker.invalid/video/BV123", "https://bilibili.com@attacker.invalid/video/BV123", "https://evilbilibili.com/video/BV123", "file:///tmp/video", "not a url")) {
            row.setUrl(url);
            assertNull(HomeSectionService.video(row).getResumeUrl(), url);
        }
        row.setUrl(null);
        row.setBvid("BV123/../../evil");
        assertNull(HomeSectionService.video(row).getResumeUrl());
    }

    @Test
    void testFinance_跨年月份边界与精确结余() {
        var start = LocalDate.of(2026, 12, 1);
        var end = LocalDate.of(2027, 1, 1);
        when(mapper.incomeTotal(7, start, end)).thenReturn(new BigDecimal("100.10"));
        when(mapper.expenseTotal(7, start.atStartOfDay(), end.atStartOfDay())).thenReturn(new BigDecimal("30.20"));
        var result = service.finance(7, LocalDate.of(2026, 12, 31));
        assertEquals("2026-12", result.month());
        assertEquals("month", result.period());
        assertEquals("2026年12月", result.label());
        assertEquals(start, result.startDate());
        assertEquals(end, result.endDate());
        assertEquals(new BigDecimal("69.90"), result.balance());
        verify(mapper).incomeTotal(7, start, end);
        verify(mapper).expenseTotal(7, start.atStartOfDay(), end.atStartOfDay());
    }

    @Test
    void testAccess_任何来源权限拒绝时不查询业务数据() {
        var denied = new ResponseStatusException(HttpStatus.FORBIDDEN);
        doThrow(denied).when(access).check(7, "/finance/dashboard", "/finance/income", "/finance/expense");
        doThrow(denied).when(access).check(7, "/record/memo");
        doThrow(denied).when(access).check(7, "/record/videoWatch");
        doThrow(denied).when(access).check(7, "/my-hub/device");
        assertThrows(ResponseStatusException.class, () -> service.finance(7));
        assertThrows(ResponseStatusException.class, () -> service.memos(7));
        assertThrows(ResponseStatusException.class, () -> service.videos(7));
        assertThrows(ResponseStatusException.class, () -> service.devices(7));
        verifyNoInteractions(mapper);
    }

    @Test
    void testFinance_年度跨年边界且不冒充月份() {
        var start = LocalDate.of(2024, 1, 1);
        var end = LocalDate.of(2025, 1, 1);
        when(mapper.incomeTotal(7, start, end)).thenReturn(new BigDecimal("100.10"));
        when(mapper.expenseTotal(7, start.atStartOfDay(), end.atStartOfDay())).thenReturn(new BigDecimal("30.20"));
        var result = service.finance(7, "year", LocalDate.of(2024, 2, 29));
        assertEquals("year", result.period());
        assertEquals("2024年", result.label());
        assertEquals(start, result.startDate());
        assertEquals(end, result.endDate());
        assertNull(result.month());
        assertEquals(new BigDecimal("69.90"), result.balance());
        verify(mapper).incomeTotal(7, start, end);
        verify(mapper).expenseTotal(7, start.atStartOfDay(), end.atStartOfDay());
    }

    @Test
    void testFinance_全部区间没有日期或月份边界且允许负结余() {
        when(mapper.incomeTotal(7, null, null)).thenReturn(new BigDecimal("30.10"));
        when(mapper.expenseTotal(7, null, null)).thenReturn(new BigDecimal("100.20"));
        var result = service.finance(7, "all", today);
        assertEquals("all", result.period());
        assertEquals("全部", result.label());
        assertNull(result.startDate());
        assertNull(result.endDate());
        assertNull(result.month());
        assertEquals(new BigDecimal("-70.10"), result.balance());
        verify(mapper).incomeTotal(7, null, null);
        verify(mapper).expenseTotal(7, null, null);
    }

    @Test
    void testIncome_仅检查收入权限且不查询支出() {
        when(mapper.incomeTotal(7, null, null)).thenReturn(new BigDecimal("10.20"));
        var result = service.income(7, "all");
        assertEquals(new BigDecimal("10.20"), result.amount());
        assertEquals("all", result.period());
        assertNull(result.startDate());
        assertNull(result.endDate());
        verify(access).check(7, "/finance/income");
        verify(mapper).incomeTotal(7, null, null);
        verifyNoMoreInteractions(access, mapper);
    }

    @Test
    void testExpense_仅检查支出权限且不查询收入() {
        when(mapper.expenseTotal(7, null, null)).thenReturn(new BigDecimal("3.40"));
        var result = service.expense(7, "all");
        assertEquals(new BigDecimal("3.40"), result.amount());
        assertEquals("all", result.period());
        assertNull(result.month());
        verify(access).check(7, "/finance/expense");
        verify(mapper).expenseTotal(7, null, null);
        verifyNoMoreInteractions(access, mapper);
    }

    @Test
    void testPeriod_非法范围在所有财务入口拒绝且不查询数据库() {
        for (var period : List.of("week", "YEAR", "2026-10", " all ")) {
            assertEquals(HttpStatus.BAD_REQUEST,
                    assertThrows(ResponseStatusException.class, () -> service.finance(7, period)).getStatusCode());
            assertEquals(HttpStatus.BAD_REQUEST,
                    assertThrows(ResponseStatusException.class, () -> service.income(7, period)).getStatusCode());
            assertEquals(HttpStatus.BAD_REQUEST,
                    assertThrows(ResponseStatusException.class, () -> service.expense(7, period)).getStatusCode());
        }
        verifyNoInteractions(mapper);
    }

    @Test
    void testAccess_独立收支入口拒绝时不查询数据库() {
        var denied = new ResponseStatusException(HttpStatus.FORBIDDEN);
        doThrow(denied).when(access).check(7, "/finance/income");
        doThrow(denied).when(access).check(7, "/finance/expense");
        assertThrows(ResponseStatusException.class, () -> service.income(7, "all"));
        assertThrows(ResponseStatusException.class, () -> service.expense(7, "year"));
        verifyNoInteractions(mapper);
    }

    @Test
    void testImages_摘要映射保留图片文件ID与封面状态() {
        var video = new BVideoEntity();
        video.setCoverFileId("9007199254740993");
        video.setCoverState("READY");
        assertEquals("9007199254740993", HomeSectionService.video(video).getCoverFileId());
        assertEquals("READY", HomeSectionService.video(video).getCoverState());
        var device = device(today, "10");
        device.setFileId("9007199254740995");
        assertEquals("9007199254740995", HomeSectionService.device(device, today).getFileId());
    }

}
