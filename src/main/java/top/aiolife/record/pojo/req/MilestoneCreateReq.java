package top.aiolife.record.pojo.req;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.OptBoolean;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.Data;

/** MilestoneCreateReq：仅包含接口允许写入的字段。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MilestoneCreateReq {

    private String title;

    private String description;

    @NotNull
    @Schema(description = "开始日期，格式 yyyy-MM-dd")
    @JsonFormat(shape = JsonFormat.Shape.STRING, lenient = OptBoolean.FALSE)
    private LocalDate date;

    @Schema(description = "结束日期，格式 yyyy-MM-dd；未记录时为空")
    @JsonFormat(shape = JsonFormat.Shape.STRING, lenient = OptBoolean.FALSE)
    private LocalDate endDate;

    private String type;

    private String tags;
}
