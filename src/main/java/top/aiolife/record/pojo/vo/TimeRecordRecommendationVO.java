package top.aiolife.record.pojo.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.*;
import java.util.*;
import lombok.Data;

/** TimeRecordRecommendationVO：接口专用字段。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TimeRecordRecommendationVO {
    private TimeRecordListVO recommend;
    private List<TimeRecordListVO> records;
}
