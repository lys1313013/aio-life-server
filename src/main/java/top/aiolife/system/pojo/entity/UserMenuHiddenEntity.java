package top.aiolife.system.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import top.aiolife.core.pojo.entity.BaseEntity;

/** 用户隐藏菜单。保留统一字段，恢复显示时使用物理删除。 */
@Getter
@Setter
@TableName("user_menu_hidden")
public class UserMenuHiddenEntity extends BaseEntity {
    private Long userId;
    private Long menuId;
}
