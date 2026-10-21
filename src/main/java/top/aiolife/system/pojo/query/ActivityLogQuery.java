package top.aiolife.system.pojo.query;

import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

@Getter
@Setter
public class ActivityLogQuery {
    private int page = 1;
    private int pageSize = 20;
    private String username;
    public LocalDate getEndExclusive() { return endDate == null ? null : endDate.plusDays(1); }

    public String getUsernameLike() { return like(username); }

    private static String like(String value) {
        if (value == null || value.isBlank()) return null;
        return "%" + value.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    public void validateRange() {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("分页参数无效");
        }
        if (username != null && username.length() > 100) {
            throw new IllegalArgumentException("用户账号不能超过100个字符");
        }
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("开始日期不能晚于结束日期");
        }
    }
}
