package top.aiolife.sso.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import top.aiolife.core.pojo.entity.IdentityEntity;

@Getter
@Setter
@ToString
@TableName("message")
public class MessageEntity extends IdentityEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long senderId;

    private Long receiverId;

    private String title;

    private String content;

    /**
     * 消息类型: 0-系统通知, 1-用户消息
     */
    private Integer type;

    @TableField("is_read")
    private Boolean isRead;

    private Long createUser;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    private Long updateUser;

    @TableLogic
    private Integer isDeleted;

    @Override
    protected Object identityKey() {
        return id;
    }
}
