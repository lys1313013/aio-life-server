package top.aiolife.record.service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** 起止时间均为完整日期，不补齐缺失的月份或日期。 */
public final class LifeFootprintDates {
    public static final LocalDate PRESENT = LocalDate.of(9999, 12, 31);

    private LifeFootprintDates() {}

    public static LocalDate parse(String value) {
        try {
            if (value != null && value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
                var date = LocalDate.parse(value);
                if (date.getYear() >= 1000) return date;
            }
        } catch (DateTimeParseException ignored) {
            // 统一转为业务输入错误，不暴露解析器内容。
        }
        throw invalid("请填写有效的完整日期 YYYY-MM-DD");
    }

    public static void validate(String start, String end, LocalDate today) {
        var begin = parse(start);
        var finish = parse(end);
        if (begin.isAfter(today)) throw invalid("开始日期不能位于未来");
        if (!finish.equals(PRESENT) && finish.isAfter(today)) throw invalid("结束日期不能位于未来");
        if (finish.isBefore(begin)) throw invalid("结束日期不能早于开始日期");
    }

    private static ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
