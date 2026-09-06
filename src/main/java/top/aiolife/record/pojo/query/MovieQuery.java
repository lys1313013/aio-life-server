package top.aiolife.record.pojo.query;

import lombok.Data;

import java.util.List;

@Data
public class MovieQuery {
    private String title;
    private String director;
    private Integer type;
    private Integer status;
    private List<Integer> statuses;
    private Boolean activeOnly;
    private Integer current;
    private Integer size;
}
