package top.aiolife.export.api;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotRoleException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import top.aiolife.core.exception.SecondaryLockRequiredException;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.export.service.ExportAuditRecorder;
import top.aiolife.export.service.ExportException;
import top.aiolife.sso.util.RequestLoginContext;

import java.util.Map;

/** 捕获包含拦截器在内的拒绝；只保存安全错误码，永不记录异常原文。 */
@RestControllerAdvice(assignableTypes = {ExportController.class, ExportAuditController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
@Slf4j
public class ExportExceptionHandler {
    private final ExportAuditRecorder audit;

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handle(
            Exception error, HttpServletResponse response, HttpServletRequest request) {
        String code = "EXPORT_UNAVAILABLE", message = "数据导出暂不可用，请稍后重试";
        HttpStatus status = HttpStatus.SERVICE_UNAVAILABLE;
        Map<String, String> data;
        if (error instanceof ExportException export) {
            code = export.getErrorCode();
            message = export.getReason();
            status = HttpStatus.valueOf(export.getStatusCode().value());
        } else if (error instanceof NotLoginException) {
            code = "EXPORT_LOGIN_REQUIRED";
            message = "请先登录";
            status = HttpStatus.UNAUTHORIZED;
        } else if (error instanceof NotRoleException) {
            code = "EXPORT_FORBIDDEN";
            message = "无权访问";
            status = HttpStatus.FORBIDDEN;
        } else if (error instanceof SecondaryLockRequiredException) {
            code = "EXPORT_LOCKED";
            message = "请先解锁相关菜单";
            status =
                    request.getRequestURI().endsWith("/download")
                            ? HttpStatus.FORBIDDEN
                            : HttpStatus.OK;
        } else if (error instanceof IllegalArgumentException
                || error instanceof MethodArgumentNotValidException
                || error instanceof HttpMessageNotReadableException
                || error instanceof MissingRequestHeaderException) {
            code = "EXPORT_INVALID_REQUEST";
            message = "导出请求参数无效";
            status = HttpStatus.BAD_REQUEST;
        }
        try {
            audit.independent(
                    RequestLoginContext.userIdOrNull(), null, "REQUEST_DENIED", code, null);
        } catch (RuntimeException unavailable) {
            log.error("导出拒绝事件未落库，code={}", code);
        }
        if (response.isCommitted()) return null;
        // 文件流尚未写出时清除下载响应头，避免错误 JSON 被当作附件或沿用文件长度。
        response.reset();
        data =
                error instanceof SecondaryLockRequiredException locked
                        ? Map.of("menuPath", locked.getMenuPath())
                        : Map.of("errorCode", code);
        var builder = ResponseEntity.status(status);
        if (status == HttpStatus.TOO_MANY_REQUESTS) builder.header("Retry-After", "60");
        return builder.body(
                ApiResponse.error(
                        error instanceof SecondaryLockRequiredException ? 2001 : 4001,
                        message,
                        data));
    }
}
