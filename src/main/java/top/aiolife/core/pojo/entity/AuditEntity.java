package top.aiolife.core.pojo.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 统一审计字段。
 */
@Getter
@Setter
public abstract class AuditEntity extends IdentityEntity {

    private Long createUser;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    private Long updateUser;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
