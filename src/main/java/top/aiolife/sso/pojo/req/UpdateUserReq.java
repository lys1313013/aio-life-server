package top.aiolife.sso.pojo.req;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.Data;

/**
 * 更新用户信息请求参数
 *
 * @author Lys
 * @date 2026/3/1
 */
@Data
public class UpdateUserReq {

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 个人简介
     */
    private String introduction;

    /**
     * 头像文件 ID
     */
    private String avatarFileId;
    @JsonIgnore
    private boolean avatarFileIdSpecified;

    @JsonSetter("avatarFileId")
    public void setAvatarFileId(String avatarFileId) {
        this.avatarFileId = avatarFileId;
        this.avatarFileIdSpecified = true;
    }
}
