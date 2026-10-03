package top.aiolife.sso.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.sql.Timestamp;

/**
 * 登录日志
 *
 * @author Lys
 * @date 2025/06/22 20:45
 */
@Data
@TableName("login_log")
public class LoginLogEntity {
    /**
     * 主键
     */
    private Long id;
    /**
     * 用户id
     */
    private Long userId;
    /**
     * 用户名
     */
    private String username;
    /**
     * 历史兼容字段；新登录日志不再写入密码，历史记录须单独按保留策略清理。
     */
    private String password;
    /**
     * 创建时间
     */
    private Timestamp createdAt;
    /**
     * ip地址
     */
    private String ipAddress;
}
