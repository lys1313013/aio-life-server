package top.aiolife.config;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.test.util.ReflectionTestUtils;
import top.aiolife.bankcard.pojo.vo.BankCardCoverTemplateVO;
import top.aiolife.membership.api.MembershipController;
import top.aiolife.membership.pojo.dto.MembershipStatsAggregate;
import top.aiolife.membership.mapper.IMembershipMapper;
import top.aiolife.membership.service.MembershipProviderService;
import top.aiolife.membership.service.MembershipRecordWriteService;
import top.aiolife.sso.api.MessageController;
import top.aiolife.sso.service.IMessageService;
import top.aiolife.wardrobe.mapper.WardrobeCategoryMapper;
import top.aiolife.wardrobe.mapper.WardrobeItemMapper;
import top.aiolife.wardrobe.pojo.dto.WardrobeCategoryCount;
import top.aiolife.wardrobe.pojo.dto.WardrobeStatsAggregate;
import top.aiolife.wardrobe.service.impl.WardrobeItemServiceImpl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** 使用当前 Mapper 聚合结果执行业务响应转换，再用生产 ObjectMapper 验证 JSON 数值契约。 */
class CountResponseContractTest {
    private final ObjectMapper mapper = new JsonConfig()
            .jacksonObjectMapper(new Jackson2ObjectMapperBuilder());

    @Test
    void 卡面使用数量为整数且ID保持字符串() {
        for (int count : new int[]{0, 1, Integer.MAX_VALUE}) {
            var cover = new BankCardCoverTemplateVO("9007199254740993", "卡面", "9007199254740995",
                    "银行", "debit", null, 1, 0, "a".repeat(32), count);
            JsonNode json = mapper.valueToTree(cover.withPublicUrl("https://example.test/cover"));
            assertNumber(json.path("usageCount"), count);
            assertEquals("9007199254740993", json.path("id").textValue());
            assertEquals("9007199254740995", json.path("bankId").textValue());
        }
    }

    @Test
    void 会员聚合结果保留金额且零和正数均输出数字() {
        var membershipMapper = mock(IMembershipMapper.class);
        var recordService = new MembershipRecordWriteService(membershipMapper, mock(MembershipProviderService.class));
        var controller = new MembershipController(recordService, mock(MembershipProviderService.class));
        var totals = new MembershipStatsAggregate();
        totals.setActiveCount(1);
        totals.setExpiringCount(1);
        totals.setExpiredCount(1);
        totals.setExpiringThisMonthCount(1);
        totals.setMonthlyAmount(new BigDecimal("20"));
        var empty = new MembershipStatsAggregate();
        empty.setMonthlyAmount(BigDecimal.ZERO);
        when(membershipMapper.aggregateStats(eq(11L), any(LocalDate.class), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(totals, empty);
        try (var login = mockStatic(StpUtil.class)) {
            login.when(StpUtil::getLoginIdAsLong).thenReturn(11L);
            JsonNode json = mapper.valueToTree(controller.stats().getData());
            for (String name : List.of("activeCount", "expiringCount", "expiredCount", "expiringThisMonthCount")) {
                assertNumber(json.path(name), 1);
            }
            assertEquals(0, new BigDecimal("20").compareTo(json.path("monthlyAmount").decimalValue()));
            json = mapper.valueToTree(controller.stats().getData());
            for (String name : List.of("activeCount", "expiringCount", "expiredCount", "expiringThisMonthCount")) {
                assertNumber(json.path(name), 0);
            }
            assertEquals(0, json.path("monthlyAmount").decimalValue().signum());
        }
    }

    @Test
    void 衣柜分类和季节数量输出数字且金额计算不变() {
        var categories = mock(WardrobeCategoryMapper.class);
        var items = mock(WardrobeItemMapper.class);
        var service = new WardrobeItemServiceImpl(categories);
        ReflectionTestUtils.setField(service, "baseMapper", items);
        when(items.aggregateStats(11L)).thenReturn(
                new WardrobeStatsAggregate(2, new BigDecimal("40"), 2, 0, 2, 0),
                new WardrobeStatsAggregate(0, BigDecimal.ZERO, 0, 0, 0, 0));
        when(items.aggregateCategoryCounts(11L))
                .thenReturn(List.of(new WardrobeCategoryCount(null, 2)), List.of());
        JsonNode json = mapper.valueToTree(service.getStats(11L));
        assertNumber(json.path("totalCount"), 2);
        assertNumber(json.path("categoryCount").path("未分类"), 2);
        assertNumber(json.path("seasonCount").path("春"), 2);
        assertNumber(json.path("seasonCount").path("夏"), 0);
        assertNumber(json.path("seasonCount").path("秋"), 2);
        assertEquals(0, new BigDecimal("40").compareTo(json.path("totalValue").decimalValue()));
        assertEquals(0, new BigDecimal("20").compareTo(json.path("avgPrice").decimalValue()));
        json = mapper.valueToTree(service.getStats(11L));
        assertNumber(json.path("totalCount"), 0);
        assertTrue(json.path("categoryCount").isEmpty());
        json.path("seasonCount").forEach(value -> assertNumber(value, 0));
        assertEquals(0, json.path("totalValue").decimalValue().signum());
        assertEquals(0, json.path("avgPrice").decimalValue().signum());
    }

    @Test
    void 未读数转换为整数且超界不静默截断() {
        var service = mock(IMessageService.class);
        var controller = new MessageController(service);
        try (var login = mockStatic(StpUtil.class)) {
            login.when(StpUtil::getLoginIdAsLong).thenReturn(11L);
            for (long count : new long[]{0L, 2L, Integer.MAX_VALUE}) {
                when(service.getUnreadCount(11L)).thenReturn(count);
                JsonNode json = mapper.valueToTree(controller.unreadCount().getData());
                assertNumber(json.path("count"), (int) count);
            }
            when(service.getUnreadCount(11L)).thenReturn((long) Integer.MAX_VALUE + 1);
            assertThrows(ArithmeticException.class, controller::unreadCount);
        }
    }

    private void assertNumber(JsonNode value, int expected) {
        assertTrue(value.isIntegralNumber(), value.toString());
        assertEquals(expected, value.intValue());
    }
}
