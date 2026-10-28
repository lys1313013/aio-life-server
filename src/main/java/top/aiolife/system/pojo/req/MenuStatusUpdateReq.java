package top.aiolife.system.pojo.req;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import java.time.*;
import java.util.*;
import lombok.Data;

/** MenuStatusUpdateReq：接口专用字段。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MenuStatusUpdateReq {
    @NotNull @Min(0) @Max(1)
    private Integer isEnabled;
}
