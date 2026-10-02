package top.aiolife.system.pojo.req;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotNull;
import java.time.*;
import java.util.*;
import lombok.Data;

/** MenuSortUpdateReq：接口专用字段。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MenuSortUpdateReq {
    @NotNull
    private Integer sort;
}
