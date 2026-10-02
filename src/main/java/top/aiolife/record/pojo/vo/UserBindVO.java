package top.aiolife.record.pojo.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/** UserBindVO：仅包含接口需要返回的字段。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserBindVO {

    private Long id;

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
    private String accessToken;

    /**
     * 额外配置(JSON)
     */
    private String metaFields;
}
