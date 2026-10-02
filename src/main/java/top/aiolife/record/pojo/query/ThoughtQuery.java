package top.aiolife.record.pojo.query;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/** ThoughtQuery：仅包含接口支持筛选的字段。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ThoughtQuery {

    private Long id;

    private Integer isPinned;

    private Boolean hiddenContent;

    private String content;
}
