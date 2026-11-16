package top.aiolife.record.pojo.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.OptBoolean;
import java.time.LocalDate;
import lombok.Data;

/** MilestoneVO：仅包含接口需要返回的字段。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MilestoneVO {

    private Long id;

    private String title;

    private String description;

    @JsonFormat(shape = JsonFormat.Shape.STRING, lenient = OptBoolean.FALSE)
    private LocalDate date;

    @JsonFormat(shape = JsonFormat.Shape.STRING, lenient = OptBoolean.FALSE)
    private LocalDate endDate;

    private String type;

    private String tags;
}
