package top.aiolife.system.aop;

import io.swagger.v3.oas.annotations.Operation;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;
import top.aiolife.core.constant.ResponseCodeConst;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.util.LogSafeException;
import top.aiolife.sso.pojo.req.LoginReq;
import top.aiolife.sso.pojo.vo.UserLoginVO;
import top.aiolife.sso.util.RequestLoginContext;
import top.aiolife.system.filter.ActivityLogTimingFilter;
import top.aiolife.system.pojo.entity.ActivityLogEntity;
import top.aiolife.system.service.ActivityLogParameters;
import top.aiolife.system.service.ActivityLogRecorder;

/** 仅采集到达 Controller 的请求，鉴权拦截失败不计为业务操作。 */
@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class ActivityLogAspect {
    private final ActivityLogRecorder recorder;
    private final ActivityLogParameters parameters;

    @Around("@within(org.springframework.web.bind.annotation.RestController) && execution(public * *(..))")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) return point.proceed();
        var request = attributes.getRequest();
        long startedNanos = request.getAttribute(ActivityLogTimingFilter.START_NANOS) instanceof Long nanos ? nanos : System.nanoTime();
        LocalDateTime startedTime = request.getAttribute(ActivityLogTimingFilter.START_TIME) instanceof LocalDateTime time
                ? time : LocalDateTime.now(ZoneId.of("Asia/Shanghai")).truncatedTo(ChronoUnit.MILLIS);
        String path = request.getRequestURI().substring(request.getContextPath().length());
        boolean login = "/auth/login".equals(path);
        boolean logout = "/auth/logout".equals(path);
        // 会话心跳、鉴权辅助、文件读取不属于用户主动操作。
        if ("OPTIONS".equals(request.getMethod()) || path.startsWith("/actuator/") || path.equals("/auth/info") || path.equals("/auth/codes") || path.equals("/user/info")
                || path.equals("/message/unread-count") || path.startsWith("/file/preview/")
                || path.startsWith("/file/download/")) return point.proceed();

        ActivityLogEntity row = new ActivityLogEntity();
        try {
            // 登录账号只取登录表单用户名，不取密码。登出前保存身份。
            row.setUserId(login ? null : RequestLoginContext.userIdOrNull());
            if (login) for (Object arg : point.getArgs()) if (arg instanceof LoginReq req) row.setUsername(req.getUsername());
        } catch (Exception error) {
            log.warn("无法读取日志用户身份", LogSafeException.withoutMessages(error));
            return point.proceed();
        }
        if (!login && row.getUserId() == null) return point.proceed();
        row.fillCreateCommonField(row.getUserId());
        row.setLogType(login || logout ? "ACCESS" : "OPERATION");
        row.setAccessType(login ? "账密登录" : logout ? "登出" : null);
        // 来源地址使用直连/反向代理解析后的地址，避免直接信任客户端伪造的 XFF。
        row.setIpAddress(request.getRemoteAddr());
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        row.setRequestPath(pattern == null ? null : pattern.toString());
        if ("OPERATION".equals(row.getLogType())) {
            row.setRequestTime(startedTime);
            row.setRequestMethod(request.getMethod());
            try {
                row.setServerIp(parameters.serverIp());
                row.setRequestParams(parameters.capture(request, point, row.getRequestPath()));
            } catch (Exception ignored) {
                // 日志采集错误不影响业务；不输出可能包含原始参数的异常。
            }
        }
        row.setFunctionName("接口操作");
        if (point.getSignature() instanceof MethodSignature signature) {
            Operation operation = signature.getMethod().getAnnotation(Operation.class);
            if (operation != null && !operation.summary().isBlank()) row.setFunctionName(operation.summary());
        }
        row.setFunctionItem(action(request.getMethod(), point.getSignature().getName()));
        row.setSuccess(false);
        try {
            Object result = point.proceed();
            row.setSuccess(!(result instanceof ApiResponse<?> response) || ResponseCodeConst.SUCCESS == response.getCode());
            if (login && result instanceof ApiResponse<?> response && response.getData() instanceof UserLoginVO user) {
                row.setUserId(user.getId());
                row.setCreateUser(user.getId());
                row.setUpdateUser(user.getId());
            }
            return result;
        } finally {
            if ("OPERATION".equals(row.getLogType())) {
                long elapsed = (System.nanoTime() - startedNanos) / 1_000_000;
                if (elapsed >= 0 && elapsed <= Integer.MAX_VALUE) row.setDurationMs((int) elapsed);
                else log.warn("操作日志耗时超出范围");
            }
            try {
                recorder.record(row, request.getHeader("X-Page-Path"), request.getHeader("User-Agent"));
            } catch (Exception error) {
                // 日志库不可用时不改变原业务返回；保留可监测的服务端错误。
                log.error("行为日志写入失败，type={}, path={}", row.getLogType(), row.getRequestPath(), LogSafeException.withoutMessages(error));
            }
        }
    }

    static String action(String method, String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("export")) return "导出";
        if ("DELETE".equals(method) || lower.startsWith("delete") || lower.startsWith("remove")) return "删除";
        if ("GET".equals(method) || lower.startsWith("query") || lower.startsWith("list") || lower.startsWith("get")) return "查询";
        if ("PUT".equals(method) || "PATCH".equals(method) || lower.startsWith("update") || lower.startsWith("modify")) return "修改";
        if (lower.startsWith("add") || lower.startsWith("create")) return "新增";
        return "提交";
    }
}
