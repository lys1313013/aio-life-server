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
    private Integer current;
    private Integer size;
}
