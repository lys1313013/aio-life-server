package top.aiolife.record.pojo.query;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/** MemoQuery：仅包含接口支持筛选的字段。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MemoQuery {

    /**
     * 内容
     */
    private String content;
}
