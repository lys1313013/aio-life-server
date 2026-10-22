package top.aiolife.record.pojo.query;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import top.aiolife.core.query.PaginationRequest;
import top.aiolife.record.pojo.enums.ProgressStatusEnum;

import java.util.List;

@Data
public class ReadRecordQuery implements PaginationRequest {
    private String title;
    private Integer type;
    private ProgressStatusEnum status;
    private List<ProgressStatusEnum> statuses;
    private Boolean activeOnly;
    /** 时迹关联选择时，优先展示在读记录。 */
    private Boolean inProgressFirst;
    @NotNull @Min(1) @JsonAlias("current")
    @Schema(description = "页码，从 1 开始", defaultValue = "1")
    private Integer page = 1;
    @NotNull @Min(1) @Max(MAX_PAGE_SIZE) @JsonAlias("size")
    @Schema(description = "每页条数，最多 200", defaultValue = "10")
    private Integer pageSize = 10;

    /** 兼容内部旧调用；HTTP 参数以 page/pageSize 为准。 */
    @JsonIgnore @Schema(hidden = true)
    public Integer getCurrent() { return page; }
    public void setCurrent(Integer current) { this.page = current; }
    @JsonIgnore @Schema(hidden = true)
    public Integer getSize() { return pageSize; }
    public void setSize(Integer size) { this.pageSize = size; }
}
