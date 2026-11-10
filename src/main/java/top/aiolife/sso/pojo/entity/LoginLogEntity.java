package top.aiolife.sso.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.sql.Timestamp;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import top.aiolife.core.pojo.entity.IdentityEntity;

/**
 * 登录日志
 *
 * @author Lys
 * @date 2025/06/22 20:45
 */
@Getter
@Setter
@ToString
@TableName("login_log")
public class LoginLogEntity extends IdentityEntity {
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
     * 创建时间
     */
    private Timestamp createdAt;
    /**
     * ip地址
     */
    private String ipAddress;

    @Override
    protected Object identityKey() {
        return id;
    }
}
