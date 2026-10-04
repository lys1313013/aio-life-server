package top.aiolife.record.pojo.query;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.Data;

/** TimeRecordQuery：仅包含接口支持筛选的字段。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TimeRecordQuery {

    @NotNull(message = "date 不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;
}
