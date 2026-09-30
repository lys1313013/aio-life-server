package top.aiolife.sso.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import top.aiolife.core.pojo.entity.AuditEntity;

import java.time.LocalDateTime;

/**
 * 类功能描述
 *
 * @author Lys
 * @date 2025/4/3
 */
@Getter
@Setter
@TableName("user")
public class UserEntity extends AuditEntity {

    /**
     * 主键
     */
    private Long id;

    /**
     * 用户名
     */
    private String username;

    /**
     * 密码
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    /**
     * 密码盐
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String passwordSalt;

    /**
     * 二级密码
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String secondaryPassword;

    /**
     * 二级密码盐
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String secondaryPasswordSalt;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 头像
     */
    private String avatar;

    /**
     * 邮箱
     */
    private String email;

    /** 国际电话区号，不含 +；与 phone 同时为空或同时有值。 */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String phoneCountryCode;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String phone;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private LocalDateTime phoneVerifiedAt;

    /** 仅对应服务端配置的一个微信小程序，不作为跨应用身份。 */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String wechatOpenid;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String wechatUnionid;

    /**
     * 角色类型
     */
    private String role;

    /**
     * 个人简介
     */
    private String introduction;

    /**
     * 是否删除
     */
    @TableLogic
    private Integer isDeleted;

    /**
     * 最后活跃时间
     */
    @TableField("last_active_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastActiveTime;
}
