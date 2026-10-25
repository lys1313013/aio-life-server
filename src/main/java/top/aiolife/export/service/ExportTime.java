package top.aiolife.export.service;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;

/** 所有配额和任务时间统一为上海时间，不使用主机默认时区。 */
public final class ExportTime {
    private ExportTime() {}

    public static LocalDateTime now() {
        return LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
    }

    public static String month(LocalDateTime time) {
        return YearMonth.from(time).toString();
    }
}
