package top.aiolife.record.pojo.query;

import lombok.Data;
import top.aiolife.record.pojo.enums.ProgressStatusEnum;

import java.util.List;

@Data
public class MovieQuery {
    private String title;
    private String director;
    private Integer type;
    private ProgressStatusEnum status;
    private List<ProgressStatusEnum> statuses;
    private Boolean activeOnly;
    private Integer current;
    private Integer size;
}
