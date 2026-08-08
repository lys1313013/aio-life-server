package top.aiolife.core.exception;

import cn.dev33.satoken.exception.NotLoginException;
import top.aiolife.core.constant.ResponseCodeConst;
import top.aiolife.core.resq.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理
 *
 * @author Lys
 * @date 2025/3/13
 */
@Slf4j
@RestControllerAdvice
public class ExceptionHandle {

    @ExceptionHandler(NoResourceFoundException.class)
    public ApiResponse<Object> handleNoResourceFound(NoResourceFoundException e) {
        String path = e.getResourcePath();
        log.warn("访问不存在的接口：{}", path);
        // getResourcePath() 返回不带前导斜杠的路径，如 relationships/graph
        if (path != null && path.replaceFirst("^/", "").startsWith("relationships")) {
            return ApiResponse.error(ResponseCodeConst.RSCODE_COMMON_FAIL,
                    "关系图谱功能未启用，请确认后端已开启 Neo4j 配置（AIO_LIFE_NEO4J_ENABLED=true）后重试");
        }
        return ApiResponse.error(ResponseCodeConst.RSCODE_COMMON_FAIL, "请求的接口不存在：" + path);
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Object> handleException(Exception e) {
        log.error("发生异常：{}", e.getMessage(), e);
        return ApiResponse.error(ResponseCodeConst.RSCODE_COMMON_FAIL, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Object> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldError().getDefaultMessage();
        log.error("参数校验异常：{}", message);
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
