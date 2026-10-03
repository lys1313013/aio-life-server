package top.aiolife.sso.pojo.req;

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
    @com.fasterxml.jackson.annotation.JsonIgnore
    private boolean avatarFileIdSpecified;

    @com.fasterxml.jackson.annotation.JsonSetter("avatarFileId")
    public void setAvatarFileId(String avatarFileId) {
        this.avatarFileId = avatarFileId;
        this.avatarFileIdSpecified = true;
    }
}
