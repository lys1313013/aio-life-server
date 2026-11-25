package top.aiolife.sso.pojo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class MessageConversationVO {
    private String userId;
    private String username;
    private String avatarUrl;
    @JsonIgnore
    private String avatarHash;
    @JsonIgnore
    private String avatarContentType;
    private String lastMessage;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime time;
    private int unreadCount;
}
