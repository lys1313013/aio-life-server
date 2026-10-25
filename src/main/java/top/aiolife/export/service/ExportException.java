package top.aiolife.export.service;

import lombok.Getter;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** 错误只暴露安全代码与固定提示，不包含查询参数或业务内容。 */
@Getter
public class ExportException extends ResponseStatusException {
    private final String errorCode;

    public ExportException(HttpStatus status, String errorCode, String message) {
        super(status, message);
        this.errorCode = errorCode;
    }

    public static ExportException denied() {
        return new ExportException(HttpStatus.FORBIDDEN, "EXPORT_FORBIDDEN", "无权导出所选数据");
    }

    public static ExportException missing() {
        return new ExportException(HttpStatus.NOT_FOUND, "EXPORT_NOT_FOUND", "导出任务不存在");
    }

    public static ExportException unavailable() {
        return new ExportException(
                HttpStatus.SERVICE_UNAVAILABLE, "EXPORT_UNAVAILABLE", "数据导出暂不可用");
    }
}
