package top.aiolife.sso.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import top.aiolife.core.pojo.entity.BaseEntity;

/**
 * 用户二级锁菜单关联
 *
 * @author Lys
 * @date 2026/07/25
 */
@Getter
@Setter
@TableName("user_secondary_lock_menu")
public class UserSecondaryLockMenuEntity extends BaseEntity {

    private Long userId;

    private Long menuId;
}
