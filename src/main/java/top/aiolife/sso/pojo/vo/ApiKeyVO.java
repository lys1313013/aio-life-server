package top.aiolife.sso.pojo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * API Key 视图对象
 *
 * @author Lys
 * @date 2026/03/09
 */
@Data
public class ApiKeyVO {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * API Key 前缀脱敏值，如：ak-1a2b3c4d5e***
     */
    private String apiKey;

    /**
     * 备注
     */
    private String remark;

    /**
     * 过期时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expiredAt;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /**
     * 是否过期
     */
    private Boolean isExpired;
}
