package top.aiolife.record.pojo.req;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** UserBindUpdateReq：仅包含接口允许写入的字段。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserBindUpdateReq {

    @NotNull
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
