package top.aiolife.sso.pojo.vo;

import lombok.Data;

/**
 * 用户基本信息VO
 *
 * @author Lys
 * @date 2025/04/05
 */
@Data
public class UserBasicInfoVO {
    private Long id;

    private String nickname;

    /**
     * 头像url
     */
    private String avatarFileId;

    private String avatarUrl;
}
