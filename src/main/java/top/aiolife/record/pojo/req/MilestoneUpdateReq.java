package top.aiolife.record.pojo.req;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** MilestoneUpdateReq：仅包含接口允许写入的字段。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MilestoneUpdateReq {

    @NotNull
    private Long id;

    private String title;

    private String description;

    private String date;

    private String end_date;

    private String type;

    private String tags;
}
