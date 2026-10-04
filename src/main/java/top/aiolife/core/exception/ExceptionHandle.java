package top.aiolife.core.exception;

import java.util.Map;
import cn.dev33.satoken.exception.NotLoginException;
import top.aiolife.core.constant.ResponseCodeConst;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.util.LogSafeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.server.ResponseStatusException;

/**
 * 全局异常处理
 *
 * @author Lys
 * @date 2025/3/13
 */
@Slf4j
@RestControllerAdvice
public class ExceptionHandle {

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(e.getStatusCode()).headers(e.getHeaders())
                .body(ApiResponse.error(ResponseCodeConst.RSCODE_COMMON_FAIL, "请求方法不支持"));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Object>> handleResponseStatus(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode())
                .body(ApiResponse.error(ResponseCodeConst.RECODE_PARAM_FAIL, e.getReason()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ApiResponse<Object> handleNoResourceFound(NoResourceFoundException e) {
        String path = e.getResourcePath();
        log.warn("访问不存在的接口");
        // getResourcePath() 返回不带前导斜杠的路径，如 relationships/graph
        if (path != null && path.replaceFirst("^/", "").startsWith("relationships")) {
            return ApiResponse.error(ResponseCodeConst.RSCODE_COMMON_FAIL,
                    "关系图谱功能未启用，请确认后端已开启 Neo4j 配置（AIO_LIFE_NEO4J_ENABLED=true）后重试");
        }
        return ApiResponse.error(ResponseCodeConst.RSCODE_COMMON_FAIL, "请求的接口不存在：" + path);
    }

    @ExceptionHandler(SecondaryLockRequiredException.class)
    public ApiResponse<Object> handleSecondaryLock(SecondaryLockRequiredException e) {
        return ApiResponse.error(ResponseCodeConst.SECONDARY_LOCK_REQUIRED, e.getMessage(),
                Map.of("menuPath", e.getMenuPath()));
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Object> handleException(Exception e) {
        log.error("接口调用异常", LogSafeException.withoutMessages(e));
        return ApiResponse.error(ResponseCodeConst.RSCODE_COMMON_FAIL, e.getMessage());
    }

    /**
     * 数据库/SQL 异常兜底：日志仅保留异常类型和代码栈，不记录可能含参数值的 SQL 异常消息
     * <p>
     * MyBatis/Druid SQL 异常统一收敛到 {@link DataAccessException} 父类下，具体类型优先级高于兜底
     * {@link Exception}，故不会被其透传。
     */
    @ExceptionHandler(DataAccessException.class)
    public ApiResponse<Object> handleDataAccessException(DataAccessException e) {
        log.error("数据库异常", LogSafeException.withoutMessages(e));
        return ApiResponse.error(ResponseCodeConst.RSCODE_COMMON_FAIL, "系统异常，请稍后重试");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Object> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        var fieldError = e.getBindingResult().getFieldError();
        String message = fieldError == null ? "参数校验失败" : fieldError.getDefaultMessage();
        log.warn("参数校验失败，错误数量={}", e.getBindingResult().getErrorCount());
        return ApiResponse.error(ResponseCodeConst.RECODE_PARAM_FAIL, message);
    }


    /**
     * 配合前端实现token失效时弹回登录页
     *
     * @param ex
     * @author Lys
     * @date 2025/3/13
     */
    @ExceptionHandler({NotLoginException.class})
    public ResponseEntity<String> handleUnauthorizedException(Exception ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body("未授权: " + ex.getMessage());
    }
}
