package top.aiolife.core.util;

/** 异常消息可能包含请求体、验证码邮件或 SQL 参数；日志只保留异常类型和代码栈。 */
public final class LogSafeException {
    private LogSafeException() {}

    public static Throwable withoutMessages(Throwable error) {
        return snapshot(error, 0);
    }

    private static Throwable snapshot(Throwable error, int depth) {
        if (error == null || depth >= 8) return null;
        var safe = new RuntimeException(error.getClass().getName(), snapshot(error.getCause(), depth + 1));
        safe.setStackTrace(error.getStackTrace());
        // 不复制 suppressed 异常，避免其消息再次暴露原始参数。
        return safe;
    }
}
