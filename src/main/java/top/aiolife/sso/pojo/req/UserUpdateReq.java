package top.aiolife.sso.pojo.req;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.Data;

/** UserUpdateReq：仅包含接口允许写入的字段。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserUpdateReq {

    @NotNull
    private Long id;

    /**
     * 用户名
     */
    private String username;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 头像文件 ID
     */
    private String avatarFileId;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 角色类型
     */
    private String role;

    /**
     * 个人简介
     */
    private String introduction;
    @JsonIgnore
    private boolean avatarFileIdSpecified;

    @JsonSetter("avatarFileId")
    public void setAvatarFileId(String avatarFileId) {
        this.avatarFileId = avatarFileId;
        this.avatarFileIdSpecified = true;
    }
}
