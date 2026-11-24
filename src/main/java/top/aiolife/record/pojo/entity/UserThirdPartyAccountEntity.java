package top.aiolife.record.pojo.entity;

import com.alibaba.fastjson2.annotation.JSONField;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import top.aiolife.core.pojo.entity.BaseEntity;

/**
 * user_third_party_account
 */
@TableName(value = "user_third_party_account", autoResultMap = true)
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserThirdPartyAccountEntity extends BaseEntity {

    /**
     * 本系统用户ID
     */
    private Long userId;

    /**
     * 平台类型：github, leetcode, csdn, shanbay, duolingo, douban, weread
     */
    private String platform;

    /**
     * 第三方平台的用户名/账号
     */
    private String platformUsername;

    /**
     * 访问令牌，仅运行时使用，不持久化
     */
    @JSONField(serialize = false)
    @ToString.Exclude
    @TableField(exist = false)
    private String accessToken;

    /**
     * SM4-GCM 加密的访问令牌
     */
    @ToString.Exclude
    private String accessTokenCiphertext;

    /**
     * 额外配置(JSON)
     */
    private String metaFields;
}
