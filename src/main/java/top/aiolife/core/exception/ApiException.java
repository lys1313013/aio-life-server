package top.aiolife.core.exception;

import lombok.Getter;

/**
 * 业务异常：携带业务码，由全局 advice 转换，Controller 不自行捕获。
 */
@Getter
public class ApiException extends RuntimeException {

    private final int code;

    public ApiException(int code, String message) {
        super(message);
        this.code = code;
    }

    public ApiException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}
