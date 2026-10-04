package top.aiolife.sso.wechat;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.Test;
import top.aiolife.record.aop.LogAspect;
import top.aiolife.sso.api.WechatAuthController;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WechatRequestLogTest {
    @Test void 微信所有入口都不读取日志参数避免泄露凭证() throws Throwable {
        var target = mock(WechatAuthController.class);
        for (String name : new String[]{"login", "register", "phoneLogin", "bind", "initializePassword"}) {
            var point = mock(ProceedingJoinPoint.class);
            var signature = mock(Signature.class);
            when(point.getSignature()).thenReturn(signature);
            when(signature.getName()).thenReturn(name);
            if (!name.equals("login")) when(point.getTarget()).thenReturn(target);
            when(point.proceed()).thenReturn("ok");
            assertEquals("ok", new LogAspect().doAround(point));
            verify(point, never()).getArgs();
        }
    }
}
