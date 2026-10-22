package top.aiolife.record.pojo.query;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 微信读书保留游标分页语义，统一单页条数名称。 */
@Data
public class WereadRecentQuery {
    private String cursor;
    @NotNull @Min(1) @Max(20) @JsonAlias("size")
    @Schema(description = "每批图书数量，最多 20", defaultValue = "6")
    private Integer pageSize = 6;
}
