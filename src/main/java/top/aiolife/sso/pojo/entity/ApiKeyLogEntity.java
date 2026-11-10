package top.aiolife.sso.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import top.aiolife.core.pojo.entity.IdentityEntity;

/**
 * API Key 调用日志实体
 *
 * @author Lys
 * @date 2026/03/09
 */
@Getter
@Setter
@ToString
@TableName("api_key_log")
public class ApiKeyLogEntity extends IdentityEntity {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * API Key ID
     */
    private Long apiKeyId;

    /**
     * 请求路径
     */
    private String requestPath;

    /**
     * 请求方法
     */
    private String requestMethod;

    /**
     * 响应状态码
     */
    private Integer responseStatus;

    /**
     * 客户端IP
     */
    private String clientIp;

    /**
     * 记录时间
     */
    @TableField("create_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @Override
    protected Object identityKey() {
        return id;
    }
}
