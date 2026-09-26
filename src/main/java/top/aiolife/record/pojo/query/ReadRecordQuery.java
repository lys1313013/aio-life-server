package top.aiolife.record.pojo.query;

import lombok.Data;
import top.aiolife.record.pojo.enums.ProgressStatusEnum;

import java.util.List;

@Data
public class ReadRecordQuery {
    private String title;
    private Integer type;
    private ProgressStatusEnum status;
    private List<ProgressStatusEnum> statuses;
    private Boolean activeOnly;
    /** 时迹关联选择时，优先展示在读记录。 */
    private Boolean inProgressFirst;
    private Integer current;
    private Integer size;
}
