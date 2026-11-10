package top.aiolife.sso.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import top.aiolife.core.pojo.entity.IdentityEntity;

/**
 * API Key 实体
 *
 * @author Lys
 * @date 2026/03/09
 */
@Getter
@Setter
@ToString
@TableName("api_key")
public class ApiKeyEntity extends IdentityEntity {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * API Key
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
     * 创建人
     */
    private Long createUser;

    /**
     * 创建时间
     */
    @TableField("create_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /**
     * 更新人
     */
    private Long updateUser;

    /**
     * 更新时间
     */
    @TableField("update_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    /**
     * 是否删除
     */
    @TableLogic
    private Integer isDeleted;

    @Override
    protected Object identityKey() {
        return id;
    }
}
