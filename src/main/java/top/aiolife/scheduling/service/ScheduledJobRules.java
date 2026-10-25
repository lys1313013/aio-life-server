package top.aiolife.scheduling.service;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.springframework.scheduling.support.CronExpression;

/** 首版限定中国时区和每分钟最多一次的六段 Spring Cron。 */
public final class ScheduledJobRules {
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private ScheduledJobRules() { }

    public static String validateCron(String value) {
        if (value == null || value.isBlank() || value.length() > 100) {
            throw new IllegalArgumentException("请输入有效的 Cron 表达式");
        }
        String cron = value.trim().replaceAll("\\s+", " ");
        String[] fields = cron.split(" ");
        if (fields.length != 6 || !fields[0].matches("[0-9]|[1-5][0-9]")) {
            throw new IllegalArgumentException("使用六段 Cron，秒必须为 0～59 的单个整数（每分钟最多一次）");
        }
        try {
            if (CronExpression.parse(cron).next(ZonedDateTime.now(ZONE)) == null) {
                throw new IllegalArgumentException("Cron 没有可执行的未来时间");
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Cron 表达式无效或没有未来执行时间", exception);
        }
        return cron;
    }
}
