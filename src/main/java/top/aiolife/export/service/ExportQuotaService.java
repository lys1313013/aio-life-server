package top.aiolife.export.service;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import top.aiolife.export.config.ExportProperties;
import top.aiolife.export.mapper.ExportJobMapper;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

/** 额度以任务的月桶和结算标记为账本，退款不会删除任务或审计。 */
@Service
@RequiredArgsConstructor
public class ExportQuotaService {
    private final ExportProperties properties;
    private final ExportJobMapper jobs;

    public Quota current(long userId) {
        return forMonth(userId, ExportTime.month(ExportTime.now()));
    }

    private Quota forMonth(long userId, String month) {
        int used = jobs.monthlyUsed(userId, month);
        int limit = Math.min(5, Math.max(0, properties.getMonthlyLimit()));
        return new Quota(
                month,
                limit,
                used,
                Math.max(0, limit - used),
                YearMonth.parse(month)
                        .plusMonths(1)
                        .atDay(1)
                        .atStartOfDay()
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
    }

    /** 必须在锁住当前用户行的创建事务中调用。 */
    public void requireRemaining(long userId) {
        requireRemaining(userId, ExportTime.month(ExportTime.now()));
    }

    public void requireRemaining(long userId, String month) {
        if (forMonth(userId, month).remaining() <= 0)
            throw new ExportException(
                    HttpStatus.TOO_MANY_REQUESTS, "EXPORT_QUOTA_EXCEEDED", "本月导出次数已用完，每月最多5次");
    }

    public record Quota(String month, int limit, int used, int remaining, String resetsAt) {}
}
