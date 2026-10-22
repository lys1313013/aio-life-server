package top.aiolife.sso.pojo.query;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import top.aiolife.core.query.PaginationRequest;

@Data
public class MessageAdminPageQuery implements PaginationRequest {
    @NotNull @Min(1) @JsonAlias("current")
    @Schema(description = "页码，从 1 开始", defaultValue = "1")
    private Integer page = 1;
    @NotNull @Min(1) @Max(200) @JsonAlias("size")
    @Schema(description = "每页条数，最多 200", defaultValue = "10")
    private Integer pageSize = 10;
    private Long userId;

    @Override
    public int maximumPageSize() { return 200; }
}
