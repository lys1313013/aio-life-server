package top.aiolife.record.pojo.query;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.Data;

/** TimeRecordQuery：仅包含接口支持筛选的字段。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TimeRecordQuery {

    @NotNull(message = "date 不能为空")
    @Schema(description = "时迹日期，yyyy-MM-dd")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;
}
