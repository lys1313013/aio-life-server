package top.aiolife.sso.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 邮件发送记录表
 *
 * @author Lys
 * @date 2026/03/01
 */
@Data
@TableName("mail_log")
public class MailLogEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 接收者邮箱
     */
    private String sendTo;

    /**
     * 邮件标题
     */
    private String subject;

    /**
     * 历史兼容字段；新日志使用 [REDACTED]，不保存可能含验证码的邮件正文
     */
    private String content;

    /**
     * 业务类型：register-注册, login-登录, reset_pwd-重置密码, system_notice-系统通知等
     */
    private String bizType;

    /**
     * 发送状态：1-成功，0-失败
     */
    private Integer status;

    /**
     * 失败异常类型；不保存可能包含正文或认证信息的异常消息
     */
    private String errorMsg;

    /**
     * 请求IP
     */
    private String ipAddress;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
