package top.aiolife.record.pojo.entity;

import com.alibaba.fastjson2.annotation.JSONField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import top.aiolife.core.pojo.entity.BaseEntity;

/**
 * user_bind
 */
@TableName(value = "user_bind", autoResultMap = true)
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserBindEntity extends BaseEntity {

    /**
     * 本系统用户ID
     */
    private Long userId;

    /**
     * 平台类型：github, leetcode, csdn, shanbay, douban, weread
     */
    private String platform;

    /**
     * 第三方平台的用户名/账号
     */
    private String platformUsername;

    /**
     * 访问令牌
     */
    @JSONField(serialize = false)
    @ToString.Exclude
    private String accessToken;

    /**
     * 额外配置(JSON)
     */
    private String metaFields;
}
