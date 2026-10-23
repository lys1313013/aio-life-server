package top.aiolife.record.service;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.LinkedHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import top.aiolife.record.mapper.FinanceCalendarMapper;
import top.aiolife.record.pojo.vo.FinanceCalendarVO;
import top.aiolife.record.pojo.vo.FinanceOverviewCalendarVO;
import top.aiolife.record.pojo.vo.FinanceCalendarVO.Bucket;

@Service
@RequiredArgsConstructor
public class FinanceCalendarService {
    private final FinanceCalendarMapper mapper;
    private final HomeSectionAccessGuard access;

    public FinanceCalendarVO calendar(long userId, String granularity, String date) {
        access.check(userId, "/finance/expense");
        return calendar(userId, granularity, date, LocalDate.now(ZoneId.of("Asia/Shanghai")));
    }

    public FinanceCalendarVO incomeCalendar(long userId, String granularity, String date) {
        access.check(userId, "/finance/income");
        return incomeCalendar(userId, granularity, date, LocalDate.now(ZoneId.of("Asia/Shanghai")));
    }

    @Transactional(readOnly = true)
    public FinanceOverviewCalendarVO overviewCalendar(long userId, String granularity, String date) {
        access.check(userId, "/finance/dashboard", "/finance/income", "/finance/expense");
        return overviewCalendar(userId, granularity, date, LocalDate.now(ZoneId.of("Asia/Shanghai")));
    }

    FinanceOverviewCalendarVO overviewCalendar(long userId, String granularity, String date, LocalDate today) {
        var income = incomeCalendar(userId, granularity, date, today);
        var expense = calendar(userId, granularity, date, today);
        Map<String, Bucket> scopes = new TreeMap<>();
        Map<String, BigDecimal> incomeAmounts = new LinkedHashMap<>();
        Map<String, BigDecimal> expenseAmounts = new LinkedHashMap<>();
        for (var bucket : income.buckets()) { scopes.put(bucket.key(), bucket); incomeAmounts.put(bucket.key(), bucket.amount()); }
        for (var bucket : expense.buckets()) { scopes.put(bucket.key(), bucket); expenseAmounts.put(bucket.key(), bucket.amount()); }
        var buckets = scopes.values().stream().map(bucket -> {
            var in = incomeAmounts.getOrDefault(bucket.key(), BigDecimal.ZERO);
            var out = expenseAmounts.getOrDefault(bucket.key(), BigDecimal.ZERO);
            var balance = in.subtract(out);
            return new FinanceOverviewCalendarVO.Bucket(bucket.key(), bucket.label(), bucket.startDate(), bucket.endDate(),
                    balance, bucket.future(), in, out, balance);
        }).toList();
        var balance = income.amount().subtract(expense.amount());
        return new FinanceOverviewCalendarVO(granularity, income.date(), income.period(), income.label(),
                income.startDate(), income.endDate(), income.month(), today, balance, income.amount(), expense.amount(),
                balance, buckets, income.refreshInterval());
    }

    FinanceCalendarVO incomeCalendar(long userId, String granularity, String date, LocalDate today) {
        if ("day".equals(granularity)) throw badRequest("收入 granularity 只能为 week、month 或 year");
        return calendar(userId, granularity, date, today, true);
    }

    FinanceCalendarVO calendar(long userId, String granularity, String date, LocalDate today) {
        return calendar(userId, granularity, date, today, false);
    }

    private FinanceCalendarVO calendar(long userId, String granularity, String date, LocalDate today, boolean income) {
        if (!List.of("day", "week", "month", "year").contains(granularity)) {
            throw badRequest("granularity 只能为 day、week、month 或 year");
        }
        LocalDate start = null;
        LocalDate end = null;
        String anchor = null;
        String period = "all";
        String label = "全部";
        String month = null;
        try {
            if (granularity.equals("day") || granularity.equals("week")) {
                if (date != null && !date.matches("\\d{4}-\\d{2}")) throw badRequest("date 应为 yyyy-MM");
                YearMonth selected = date == null ? YearMonth.from(today) : YearMonth.parse(date);
                start = selected.atDay(1);
                end = selected.plusMonths(1).atDay(1);
                anchor = month = selected.toString();
                period = "month";
                label = selected.getYear() + "年" + selected.getMonthValue() + "月";
            } else if (granularity.equals("month")) {
                if (date != null && !date.matches("\\d{4}")) throw badRequest("date 应为 yyyy");
                int year = date == null ? today.getYear() : Integer.parseInt(date);
                start = LocalDate.of(year, 1, 1);
                end = start.plusYears(1);
                anchor = String.valueOf(year);
                period = "year";
                label = year + "年";
            } else if (date != null) {
                throw badRequest("年视图不接受 date");
            }
            if (start != null && (start.getYear() < 1000 || start.getYear() > 9998)) {
                throw badRequest("年份应为 1000 至 9998");
            }
        } catch (DateTimeException | NumberFormatException error) {
            throw badRequest("无效的日历日期");
        }
        var points = income ? mapper.aggregateIncome(userId, start, end, granularity) : mapper.aggregate(userId, start == null ? null : start.atStartOfDay(),
                end == null ? null : end.atStartOfDay(), granularity);
        List<Bucket> buckets = new ArrayList<>();
        Map<LocalDate, BigDecimal> amounts = new TreeMap<>();
        BigDecimal undated = BigDecimal.ZERO;
        boolean hasUndated = false;
        for (var point : points) {
            if (point.date() == null) {
                undated = undated.add(point.amount());
                hasUndated = true;
            } else {
                LocalDate key = switch (granularity) {
                    case "year" -> point.date().withDayOfYear(1);
                    case "month" -> point.date().withDayOfMonth(1);
                    default -> point.date();
                };
                amounts.merge(key, point.amount(), BigDecimal::add);
            }
        }
        if (granularity.equals("year")) {
            if (amounts.isEmpty()) amounts.put(today.withDayOfYear(1), BigDecimal.ZERO);
            amounts.forEach((key, amount) -> buckets.add(bucket(key, key.plusYears(1),
                    key.getYear() + "年", amount, today)));
            if (hasUndated) buckets.add(new Bucket("undated", "未填日期", null, null, undated, false));
        } else {
            for (LocalDate cursor = start; cursor.isBefore(end);) {
                LocalDate next;
                String bucketLabel;
                if (granularity.equals("month")) {
                    next = cursor.plusMonths(1);
                    bucketLabel = cursor.getMonthValue() + "月";
                } else if (granularity.equals("week")) {
                    next = cursor.plusDays(8L - cursor.getDayOfWeek().getValue());
                    if (next.isAfter(end)) next = end;
                    bucketLabel = "第" + (buckets.size() + 1) + "周";
                } else {
                    next = cursor.plusDays(1);
                    bucketLabel = String.valueOf(cursor.getDayOfMonth());
                }
                BigDecimal amount = BigDecimal.ZERO;
                for (var entry : amounts.entrySet()) {
                    if (!entry.getKey().isBefore(cursor) && entry.getKey().isBefore(next)) {
                        amount = amount.add(entry.getValue());
                    }
                }
                buckets.add(bucket(cursor, next, bucketLabel, amount, today));
                cursor = next;
            }
        }
        BigDecimal total = buckets.stream().map(Bucket::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new FinanceCalendarVO(granularity, anchor, period, label, start, end, month, today,
                total, buckets, 300);
    }

    private static Bucket bucket(LocalDate start, LocalDate end, String label, BigDecimal amount, LocalDate today) {
        return new Bucket(start.toString(), label, start, end, amount, start.isAfter(today));
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
