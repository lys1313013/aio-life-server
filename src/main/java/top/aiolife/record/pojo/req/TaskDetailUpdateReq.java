package top.aiolife.record.pojo.req;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import top.aiolife.core.json.LocalDateTimeInputDeserializer;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

/** TaskDetailUpdateReq：仅包含接口允许写入的字段。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TaskDetailUpdateReq {

    @NotNull
    @Positive(message = "明细ID必须为正数")
    /** 待办明细 ID，从 GET /taskDetails?taskId=... 获取。 */
    @Schema(description = "待办明细 ID，从 GET /taskDetails?taskId=... 获取")
    private Long id;

    @Positive(message = "任务ID必须为正数")
    /** 所属待办主任务 ID，从 GET /tasks 查询获得。 */
    @Schema(description = "所属待办主任务 ID，从 GET /tasks 查询获得")
    private Long taskId;

    @Pattern(regexp = "(?s).*\\S.*", message = "任务明细内容不能是空白")
    @Schema(description = "待办内容")
    private String content;

    @Min(value = 0, message = "状态值必须为0或1")
    @Max(value = 1, message = "状态值必须为0或1")
    @Schema(description = "是否完成：0=未完成，1=已完成")
    private Integer isCompleted;

    @Schema(description = "列表内排序值；通常由界面拖拽维护")
    private Integer sortOrder;

    @Schema(description = "优先级：1=高，10=中，20=低", allowableValues = {"1", "10", "20"})
    private Integer priority;

    @Min(value = 0, message = "状态值必须为0或1")
    @Max(value = 1, message = "状态值必须为0或1")
    @Schema(description = "是否关注：0=否，1=是")
    private Integer isStarred;

    /**
     * 开始时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonDeserialize(using = LocalDateTimeInputDeserializer.class)
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonDeserialize(using = LocalDateTimeInputDeserializer.class)
    private LocalDateTime endTime;
    @JsonIgnore
    @AssertTrue(message = "priority 仅支持 1（高）、10（中）、20（低）")
    public boolean isPriorityValid() {
        return priority == null || List.of(1, 10, 20).contains(priority);
    }
}
