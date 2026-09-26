package top.aiolife.record.aop;

import com.alibaba.fastjson2.JSON;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;

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

        String methodName = joinPoint.getSignature().getName();
        String url = request != null ? request.getRequestURI() : "unknown";
        String httpMethod = request != null ? request.getMethod() : "unknown";


        // login 接口不打印请求参数
        if (!"login".equals(methodName) && !(joinPoint.getTarget() instanceof top.aiolife.bankcard.api.BankCardController)) {
            Object[] args = joinPoint.getArgs();
            if (args != null && args.length > 0) {
                try {
                    // 过滤掉无法序列化的对象
                    Object[] logArgs = Arrays.stream(args)
                            .filter(arg -> !(arg instanceof jakarta.servlet.ServletRequest)
                                    && !(arg instanceof jakarta.servlet.ServletResponse)
                                    && !(arg instanceof org.springframework.web.multipart.MultipartFile))
                            .toArray();
                    log.info("[{} {}], 参数：{}", httpMethod, url, JSON.toJSONString(logArgs));
                } catch (Exception e) {
                    log.info("[{} {}]", httpMethod, url);
                    log.warn("请求参数序列化失败: {}", e.getMessage());

                }
            }
        } else {
            log.info(">>> 请求参数: [PROTECTED]");
        }

        long startTime = System.currentTimeMillis();
        try {
            return joinPoint.proceed();
        } finally {
            long endTime = System.currentTimeMillis();
            log.info("耗时: {}ms", (endTime - startTime));
        }

    }
}
