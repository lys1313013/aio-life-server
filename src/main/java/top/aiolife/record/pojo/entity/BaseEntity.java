package top.aiolife.record.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;
import lombok.EqualsAndHashCode;
import top.aiolife.core.pojo.entity.AuditEntity;

import java.time.LocalDateTime;

/**
 * 类功能描述
 *
 * @author Lys
 * @date 2025/04/13 14:30
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BaseEntity extends AuditEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableLogic
    private Integer isDeleted;

    public void fillCreateCommonField(Long userId) {
        this.setCreateUser(userId);
        this.setUpdateUser(userId);
        this.setCreateTime(LocalDateTime.now());
        this.setUpdateTime(LocalDateTime.now());
        this.isDeleted = 0;
    }

    public void fillUpdateCommonField(Long userId) {
        this.setUpdateUser(userId);
        this.setUpdateTime(LocalDateTime.now());
    }
}
