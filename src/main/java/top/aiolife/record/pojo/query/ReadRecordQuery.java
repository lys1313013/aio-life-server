package top.aiolife.record.pojo.query;

import lombok.Data;

import java.util.List;

@Data
public class ReadRecordQuery {
    private String title;
    private Integer type;
    private Integer status;
    private List<Integer> statuses;
    private Boolean activeOnly;
    private Integer current;
    private Integer size;
}
