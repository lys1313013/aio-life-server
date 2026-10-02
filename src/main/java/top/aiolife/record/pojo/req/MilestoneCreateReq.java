package top.aiolife.record.pojo.req;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/** MilestoneCreateReq：仅包含接口允许写入的字段。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MilestoneCreateReq {

    private String title;

    private String description;

    private String date;

    private String end_date;

    private String type;

    private String tags;
}
