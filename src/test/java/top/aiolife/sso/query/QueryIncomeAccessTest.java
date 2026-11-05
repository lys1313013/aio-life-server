package top.aiolife.sso.query;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import top.aiolife.sso.pojo.entity.ApiKeyEntity;
import top.aiolife.sso.service.AccountStatusGuard;
import top.aiolife.sso.service.IApiKeyService;
import top.aiolife.sso.service.SecondaryLockGuard;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QueryIncomeAccessTest {
    @Test void 月收入通过请求校验且鉴权包含收入二级锁和用户归属() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertTrue(factory.getValidator().validate(new QueryAccessModels.CheckTokenRequest("income_month")).isEmpty());
            assertFalse(factory.getValidator().validate(new QueryAccessModels.CheckTokenRequest("income_admin")).isEmpty());
        }
        var props = new QueryAccessProperties(); props.setEnabled(true); props.setServiceKey("fixture-service-key-at-least-32-characters");
        var accounts = mock(AccountStatusGuard.class); var locks = mock(SecondaryLockGuard.class); var keys = mock(IApiKeyService.class);
        when(accounts.isActive(101L)).thenReturn(true);
        var key = new ApiKeyEntity(); key.setUserId(101L); when(keys.getByApiKey("ak-fixture")).thenReturn(key);
        var service = new QueryAccessService(props, mock(QueryGrantStore.class), accounts, locks, keys);
        var decision = service.checkToken(props.getServiceKey(), 101L, "ak-fixture", true, "income_month");
        assertEquals("ai_income_reader", decision.role()); assertTrue(decision.scopes().contains("income.read"));
        verify(locks).checkMenus(101L, "/mcp/tools", "/finance/income");
        key.setUserId(102L);
        assertThrows(RuntimeException.class, () -> service.checkToken(props.getServiceKey(), 101L, "ak-fixture", true, "income_month"));
        key.setUserId(101L);
        doThrow(new IllegalArgumentException("locked")).when(locks).checkMenus(101L, "/mcp/tools", "/finance/income");
        assertThrows(IllegalArgumentException.class, () -> service.checkToken(props.getServiceKey(), 101L, "ak-fixture", true, "income_month"));
    }
}
