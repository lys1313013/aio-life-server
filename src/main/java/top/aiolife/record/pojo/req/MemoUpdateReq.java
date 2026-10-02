package top.aiolife.record.pojo.req;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/** MemoUpdateReq：仅包含接口允许写入的字段。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MemoUpdateReq {

    /**
     * 标题
     */
    private String title;

    /**
     * 内容
     */
    private String content;

    /**
     * 是否隐藏内容
     */
    private Boolean hiddenContent;
}
