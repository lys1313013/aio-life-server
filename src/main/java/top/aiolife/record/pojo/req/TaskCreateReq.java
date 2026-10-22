package top.aiolife.record.pojo.req;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import top.aiolife.core.json.LocalDateTimeInputDeserializer;
import java.time.LocalDateTime;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/** TaskCreateReq：仅包含接口允许写入的字段。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TaskCreateReq {

    /**
     * 任务内容
     */
    @NotBlank(message = "任务内容不能为空")
    private String content;

    /**
     * 任务明细
     */
    private String detail;

    /**
     * 列ID
     */
    @NotNull(message = "任务列不能为空")
    @Positive(message = "任务列ID必须为正数")
    private Long columnId;

    /**
     * 目标完成时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonDeserialize(using = LocalDateTimeInputDeserializer.class)
    private LocalDateTime dueDate;

    /**
     * 排序
     */
    private Integer sortOrder;
}
