package top.aiolife.sso.pojo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import lombok.Data;

/** MessageVO：仅包含接口需要返回的字段。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MessageVO {

    private Long id;

    private Long senderId;

    private Long receiverId;

    private String title;

    private String content;

    /**
     * 消息类型: 0-系统通知, 1-用户消息
     */
    private Integer type;

    private Boolean isRead;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
