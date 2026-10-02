package top.aiolife.record.pojo.req;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/** ThoughtEventUpdateReq：仅包含接口允许写入的字段。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ThoughtEventUpdateReq {

    private Long id;

    private String content;
}
