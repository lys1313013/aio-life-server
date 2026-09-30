package top.aiolife.config;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.context.SaTokenContextForThreadLocal;
import cn.dev33.satoken.context.model.SaRequest;
import cn.dev33.satoken.context.model.SaResponse;
import cn.dev33.satoken.context.model.SaStorage;
import cn.dev33.satoken.context.second.SaTokenSecondContext;
import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.SaManager;
import top.aiolife.sso.util.RequestLoginContext;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import top.aiolife.sso.interceptor.ApiKeyInterceptor;
import top.aiolife.sso.interceptor.SecondaryLockInterceptor;
import top.aiolife.sso.interceptor.UserLastActiveInterceptor;

@Configuration
@RequiredArgsConstructor
public class SaTokenConfig implements WebMvcConfigurer {

    // 仅开放机器可读文档、单层模块分组及渐进查询入口，不放行业务接口。
    private static final String[] PUBLIC_OPENAPI_PATHS = {
            "/v3/api-docs", "/v3/api-docs/*", "/v3/api-docs.yaml", "/v3/api-docs.yaml/*",
            "/docs/catalog", "/docs/operations", "/docs/operations/*"
    };

    private final ApiKeyInterceptor apiKeyInterceptor;
    private final SecondaryLockInterceptor secondaryLockInterceptor;
    private final UserLastActiveInterceptor userLastActiveInterceptor;

    @PostConstruct
    public void initSecondContext() {
        SaTokenContextForThreadLocal threadLocalContext = new SaTokenContextForThreadLocal();
        SaManager.setSaTokenSecondContext(new SaTokenSecondContext() {
            @Override
            public SaRequest getRequest() {
                return threadLocalContext.getRequest();
            }

            @Override
            public SaResponse getResponse() {
                return threadLocalContext.getResponse();
            }

            @Override
            public SaStorage getStorage() {
                return threadLocalContext.getStorage();
            }

            @Override
            public boolean matchPath(String pattern, String path) {
                return threadLocalContext.matchPath(pattern, path);
            }

            @Override
            public boolean isValid() {
                return threadLocalContext.isValid();
            }
        });
    }

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        // API Key 拦截器，需在 Sa-Token 拦截器之前执行
        registry.addInterceptor(apiKeyInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(PUBLIC_OPENAPI_PATHS)
                .excludePathPatterns(top.aiolife.sso.api.WechatAuthController.PUBLIC_PATHS)
                .excludePathPatterns("/auth/login", "/auth/register", "/auth/sendEmailCode", "/auth/sendResetPasswordCode",
                        "/auth/resetPassword",
                        "/actuator/**");

        // 注册 Sa-Token 拦截器
        registry.addInterceptor(new SaInterceptor(handle -> {
            // CORS 预检请求直接放行
            if (RequestMethod.OPTIONS.name().equalsIgnoreCase(SaHolder.getRequest().getMethod())) {
                return;
            }
            // 如果已经通过 API Key 认证了，就不要再 checkLogin 了（实现 API Key 或 Token 二选一）
            if (Boolean.TRUE.equals(SaHolder.getStorage().get("IS_API_KEY_AUTH"))) {
                return;
            }
            RequestLoginContext.checkLogin();
        })).addPathPatterns("/**")
                .excludePathPatterns(PUBLIC_OPENAPI_PATHS)
                .excludePathPatterns(top.aiolife.sso.api.WechatAuthController.PUBLIC_PATHS)
                .excludePathPatterns("/auth/login", "/auth/register", "/auth/sendEmailCode", "/auth/sendResetPasswordCode",
                        "/auth/resetPassword",
                        "/actuator/**",
                        "/file/preview/**",
                        "/file/download/**");

        // 二级锁拦截器，在 Sa-Token 校验通过后执行
        registry.addInterceptor(secondaryLockInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(PUBLIC_OPENAPI_PATHS)
                .excludePathPatterns(top.aiolife.sso.api.WechatAuthController.PUBLIC_PATHS)
                .excludePathPatterns("/auth/login", "/auth/register", "/auth/sendEmailCode", "/auth/sendResetPasswordCode",
                        "/auth/resetPassword",
                        "/auth/secondary-verify", "/auth/secondary-password/status", "/auth/secondary-password",
                        "/auth/secondary-lock/menus",
                        "/auth/send-reset-secondary-password-code", "/auth/reset-secondary-password",
                        "/actuator/**",
                        "/file/preview/**",
                        "/file/download/**");

        // 记录最后活跃时间（仅 Token 请求），需在 Sa-Token 校验通过后执行
        registry.addInterceptor(userLastActiveInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(PUBLIC_OPENAPI_PATHS)
                .excludePathPatterns(top.aiolife.sso.api.WechatAuthController.PUBLIC_PATHS)
                .excludePathPatterns("/auth/login", "/auth/register", "/auth/sendEmailCode", "/auth/sendResetPasswordCode",
                        "/auth/resetPassword",
                        "/actuator/**",
                        "/file/preview/**",
                        "/file/download/**");
    }
}
