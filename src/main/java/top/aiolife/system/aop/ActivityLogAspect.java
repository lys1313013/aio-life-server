package top.aiolife.system.aop;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.sso.pojo.req.LoginReq;
import top.aiolife.sso.pojo.vo.UserLoginVO;
import top.aiolife.sso.util.RequestLoginContext;
import top.aiolife.system.pojo.entity.ActivityLogEntity;
import top.aiolife.system.service.ActivityLogRecorder;

/** 仅采集到达 Controller 的请求，鉴权拦截失败不计为业务操作。 */
@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class ActivityLogAspect {
    private final ActivityLogRecorder recorder;

    @Around("@within(org.springframework.web.bind.annotation.RestController) && execution(public * *(..))")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) return point.proceed();
        var request = attributes.getRequest();
        String path = request.getRequestURI().substring(request.getContextPath().length());
        boolean login = "/auth/login".equals(path);
        boolean logout = "/auth/logout".equals(path);
        // 会话心跳、鉴权辅助、文件读取不属于用户主动操作。
        if (path.equals("/auth/info") || path.equals("/auth/codes") || path.equals("/user/info")
                || path.equals("/message/unread-count") || path.startsWith("/file/preview/")
                || path.startsWith("/file/download/")) return point.proceed();

        ActivityLogEntity row = new ActivityLogEntity();
        try {
            // 登录账号只取登录表单用户名，不取密码。登出前保存身份。
            row.setUserId(login ? null : RequestLoginContext.userIdOrNull());
            if (login) for (Object arg : point.getArgs()) if (arg instanceof LoginReq req) row.setUsername(req.getUsername());
        } catch (Exception error) {
            log.warn("无法读取日志用户身份", error);
            return point.proceed();
        }
        if (!login && row.getUserId() == null) return point.proceed();
        row.fillCreateCommonField(row.getUserId());
        row.setLogType(login || logout ? "ACCESS" : "OPERATION");
        row.setAccessType(login ? "账密登录" : logout ? "登出" : null);
        // 来源地址使用直连/反向代理解析后的地址，避免直接信任客户端伪造的 XFF。
        row.setIpAddress(request.getRemoteAddr());
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        row.setRequestPath(pattern == null ? path : pattern.toString());
        row.setFunctionName("接口操作");
        row.setFunctionItem(action(request.getMethod(), point.getSignature().getName()));
        row.setSuccess(false);
        try {
            Object result = point.proceed();
            row.setSuccess(!(result instanceof ApiResponse<?> response) || "0".equals(response.getRscode()));
            if (login && result instanceof ApiResponse<?> response && response.getData() instanceof UserLoginVO user) {
                row.setUserId(user.getId());
                row.setCreateUser(user.getId());
                row.setUpdateUser(user.getId());
            }
            return result;
        } finally {
            try {
                recorder.record(row, request.getHeader("X-Page-Path"), request.getHeader("User-Agent"));
            } catch (Exception error) {
                // 日志库不可用时不改变原业务返回；保留可监测的服务端错误。
                log.error("行为日志写入失败，type={}, path={}", row.getLogType(), row.getRequestPath(), error);
            }
        }
    }

    static String action(String method, String name) {
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("export")) return "导出";
        if ("DELETE".equals(method) || lower.startsWith("delete") || lower.startsWith("remove")) return "删除";
        if ("GET".equals(method) || lower.startsWith("query") || lower.startsWith("list") || lower.startsWith("get")) return "查询";
        if ("PUT".equals(method) || "PATCH".equals(method) || lower.startsWith("update") || lower.startsWith("modify")) return "修改";
        if (lower.startsWith("add") || lower.startsWith("create")) return "新增";
        return "提交";
    }
}
