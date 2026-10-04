package top.aiolife.record.pojo.req;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import java.util.List;
import lombok.Data;

/** ThoughtUpdateReq：仅包含接口允许写入的字段。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ThoughtUpdateReq {

    private String content;

    private Integer isPinned;

    private Boolean hiddenContent;

    @Valid
    private List<ThoughtEventUpdateReq> events;
}
