package top.aiolife.config;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import top.aiolife.bankcard.pojo.vo.BankCardCoverTemplateVO;
import top.aiolife.membership.api.MembershipController;
import top.aiolife.membership.pojo.entity.MembershipRecordEntity;
import top.aiolife.membership.service.IMembershipService;
import top.aiolife.membership.service.MembershipProviderService;
import top.aiolife.membership.service.MembershipRecordWriteService;
import top.aiolife.sso.api.MessageController;
import top.aiolife.sso.service.IMessageService;
import top.aiolife.wardrobe.mapper.WardrobeCategoryMapper;
import top.aiolife.wardrobe.pojo.entity.WardrobeItemEntity;
import top.aiolife.wardrobe.service.impl.WardrobeItemServiceImpl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 执行业务计数逻辑，再用生产 ObjectMapper 验证 JSON 数值契约。 */
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
    void 会员统计计算不变且零和正数均输出数字() {
        var service = mock(IMembershipService.class);
        var controller = new MembershipController(mock(MembershipRecordWriteService.class),
                mock(MembershipProviderService.class), service);
        var active = membership(LocalDate.now().plusDays(40));
        var expiring = membership(LocalDate.now());
        var expired = membership(LocalDate.now().minusDays(1));
        when(service.list(any(Wrapper.class))).thenReturn(List.of(active, expiring, expired));
        try (var login = mockStatic(StpUtil.class)) {
            login.when(StpUtil::getLoginIdAsLong).thenReturn(11L);
            JsonNode json = mapper.valueToTree(controller.stats().getData());
            for (String name : List.of("activeCount", "expiringCount", "expiredCount", "expiringThisMonthCount")) {
                assertNumber(json.path(name), 1);
            }
            assertEquals(0, new BigDecimal("20").compareTo(json.path("monthlyAmount").decimalValue()));
            when(service.list(any(Wrapper.class))).thenReturn(List.of());
            json = mapper.valueToTree(controller.stats().getData());
            for (String name : List.of("activeCount", "expiringCount", "expiredCount", "expiringThisMonthCount")) {
                assertNumber(json.path(name), 0);
            }
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void 衣柜分类和季节数量输出数字且金额计算不变() {
        var categories = mock(WardrobeCategoryMapper.class);
        when(categories.selectList(any())).thenReturn(List.of());
        var service = spy(new WardrobeItemServiceImpl(categories));
        LambdaQueryChainWrapper<WardrobeItemEntity> query = mock(LambdaQueryChainWrapper.class, RETURNS_SELF);
        doReturn(query).when(service).lambdaQuery();
        when(query.eq(any(), any())).thenReturn(query);
        var item = new WardrobeItemEntity();
        item.setSeason("春,秋");
        item.setPrice(new BigDecimal("20"));
        when(query.list()).thenReturn(List.of(item, item));
        JsonNode json = mapper.valueToTree(service.getStats(11L));
        assertNumber(json.path("totalCount"), 2);
        assertNumber(json.path("categoryCount").path("未分类"), 2);
        assertNumber(json.path("seasonCount").path("春"), 2);
        assertNumber(json.path("seasonCount").path("夏"), 0);
        assertNumber(json.path("seasonCount").path("秋"), 2);
        assertEquals(0, new BigDecimal("40").compareTo(json.path("totalValue").decimalValue()));
        when(query.list()).thenReturn(List.of());
        json = mapper.valueToTree(service.getStats(11L));
        assertNumber(json.path("totalCount"), 0);
        assertTrue(json.path("categoryCount").isEmpty());
        json.path("seasonCount").forEach(value -> assertNumber(value, 0));
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

    private MembershipRecordEntity membership(LocalDate expiry) {
        var entity = new MembershipRecordEntity();
        entity.setExpiryDate(expiry);
        entity.setMonthlyAmount(new BigDecimal("10"));
        return entity;
    }

    private void assertNumber(JsonNode value, int expected) {
        assertTrue(value.isIntegralNumber(), value.toString());
        assertEquals(expected, value.intValue());
    }
}
