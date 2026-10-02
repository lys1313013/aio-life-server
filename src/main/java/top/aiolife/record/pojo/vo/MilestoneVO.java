package top.aiolife.record.pojo.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/** MilestoneVO：仅包含接口需要返回的字段。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MilestoneVO {

    private Long id;

    private String title;

    private String description;

    private String date;

    private String end_date;

    private String type;

    private String tags;
}
