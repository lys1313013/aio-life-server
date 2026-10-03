package top.aiolife.record.aop;

import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;

/**
 * 接口调用日志切面
 *
 * @author Lys
 * @date 2026/01/18 14:25
 */
@Aspect
@Component
public class LogAspect {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LogAspect.class);

    /**
     * 切入点：所有的 RestController
     */
    @Pointcut("@within(org.springframework.web.bind.annotation.RestController)")
    public void controllerPointcut() {}

    @Around("controllerPointcut()")
    public Object doAround(ProceedingJoinPoint joinPoint) throws Throwable {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes != null ? attributes.getRequest() : null;

        // 只记录框架路由模板，不记录原始 URL、查询参数、请求头、入参或返回值。
        // 默认禁止参数日志，新增认证入口或嵌套凭证也无需维护排除名单。
        Object pattern = request == null ? null : request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String route = pattern == null ? "[unmapped]" : pattern.toString();
        String httpMethod = request != null ? request.getMethod() : "unknown";
        long startTime = System.nanoTime();
        boolean completed = false;
        try {
            Object result = joinPoint.proceed();
            completed = true;
            return result;
        } finally {
            log.info("[{} {}] completed={} durationMs={}", httpMethod, route, completed,
                    (System.nanoTime() - startTime) / 1_000_000);
        }

    }
}
