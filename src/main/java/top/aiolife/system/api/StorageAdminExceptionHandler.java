package top.aiolife.system.api;

import cn.dev33.satoken.exception.NotRoleException;
import io.minio.errors.MinioException;
import io.minio.errors.ErrorResponseException;
import java.io.IOException;
import java.security.GeneralSecurityException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import top.aiolife.core.resq.ApiResponse;

/** 二进制请求使用明确 HTTP 错误状态，避免把业务错误 JSON 当作图片或下载内容。 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = StorageAdminController.class)
public class StorageAdminExceptionHandler {
    @ExceptionHandler(NotRoleException.class)
    public ResponseEntity<ApiResponse<Void>> forbidden() {
        return ResponseEntity.status(403).body(ApiResponse.error("403", "仅管理员可使用对象存储管理"));
    }

    @ExceptionHandler({MinioException.class, IOException.class, GeneralSecurityException.class})
    public ResponseEntity<ApiResponse<Void>> storageError(Exception exception) {
        if (exception instanceof ErrorResponseException error
                && "NoSuchKey".equals(error.errorResponse().code())) {
            return ResponseEntity.status(404).body(ApiResponse.error("404", "文件不存在或已被移除"));
        }
        log.warn("对象存储操作失败: {}", exception.getClass().getSimpleName());
        return ResponseEntity.status(502).body(ApiResponse.error("502", "对象存储操作失败，请检查配置或稍后重试"));
    }
}
