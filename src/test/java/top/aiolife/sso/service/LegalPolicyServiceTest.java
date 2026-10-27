package top.aiolife.sso.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import top.aiolife.sso.pojo.entity.UserEntity;
import top.aiolife.sso.pojo.req.RegisterReq;
import top.aiolife.sso.wechat.WechatAuthRequests;
import jakarta.validation.Validation;
import top.aiolife.system.service.ISystemConfigService;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LegalPolicyServiceTest {
    @Test void 两端政策来自同一资源且只公开指定联系配置() throws Exception {
        var config = mock(ISystemConfigService.class);
        when(config.getValueByKey("privacy.operator")).thenReturn(" Fixture Operator ");
        when(config.getValueByKey("privacy.contact-email")).thenReturn("privacy@example.test");
        var service = new LegalPolicyService(new ObjectMapper(), config);
        var policy = service.current();
        assertEquals(LegalPolicyService.VERSION, policy.version());
        assertEquals("Fixture Operator", policy.operator());
        assertEquals("privacy@example.test", policy.contactEmail());
        assertTrue(policy.privacy().stream().allMatch(section -> section.size() == 2));
        assertTrue(policy.privacy().stream().anyMatch(section -> section.get(1).contains("银行卡")));
        verify(config).getValueByKey("privacy.operator"); verify(config).getValueByKey("privacy.contact-email");
        verifyNoMoreInteractions(config);
    }
    @Test void 请求校验拒绝未同意或缺失的协议字段() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var req = new RegisterReq();
            assertFalse(validator.validate(req).isEmpty());
            req.setAgreedPolicy(false); req.setPolicyVersion(LegalPolicyService.VERSION);
            assertFalse(validator.validate(req).isEmpty());
            req.setAgreedPolicy(true);
            assertTrue(validator.validate(req).isEmpty());
            assertFalse(validator.validate(new WechatAuthRequests.Register("ticket", false, LegalPolicyService.VERSION)).isEmpty());
        }
    }

    @Test void 明确同意当前协议才记录版本与时间() {
        assertThrows(ResponseStatusException.class, () -> LegalPolicyService.requireAcceptance(null, LegalPolicyService.VERSION));
        assertThrows(ResponseStatusException.class, () -> LegalPolicyService.requireAcceptance(true, "old"));
        LegalPolicyService.requireAcceptance(true, LegalPolicyService.VERSION);
        var user = new UserEntity(); LegalPolicyService.recordAcceptance(user);
        assertEquals(LegalPolicyService.VERSION, user.getPolicyVersion()); assertNotNull(user.getPolicyAcceptedAt());
    }
}
