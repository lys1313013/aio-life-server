package top.aiolife.record.pojo.query;

import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import top.aiolife.core.query.PaginationRequest;
import top.aiolife.record.pojo.enums.ProgressStatusEnum;

import java.util.List;

@Data
public class MovieQuery implements PaginationRequest {
    private String title;
    private String director;
    private Integer type;
    @Schema(description = "状态集合，重复键传递；省略或空集合表示全部状态。首页使用 not_started 与 in_progress。")
    private List<ProgressStatusEnum> statuses;
    /** 首页优先展示在看的作品。 */
    private Boolean inProgressFirst;
    @NotNull @Min(1)
    @Schema(description = "页码，从 1 开始", defaultValue = "1")
    private Integer page = 1;
    @NotNull @Min(1) @Max(MAX_PAGE_SIZE)
    @Schema(description = "每页条数，最多 200", defaultValue = "10")
    private Integer pageSize = 10;

}
