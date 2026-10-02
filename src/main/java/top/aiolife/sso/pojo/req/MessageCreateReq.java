package top.aiolife.sso.pojo.req;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import lombok.Data;

/** MessageCreateReq：仅包含接口允许写入的字段。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MessageCreateReq {

    private Long receiverId;

    private String title;

    private String content;

    /**
     * 消息类型: 0-系统通知, 1-用户消息
     */
    private Integer type;
}
