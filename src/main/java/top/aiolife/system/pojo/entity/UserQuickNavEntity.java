package top.aiolife.system.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import top.aiolife.core.pojo.entity.BaseEntity;

/**
 * 用户首页快捷导航布局实体
 *
 * <p>存放用户最终保存的快捷导航布局；title/icon/color/url 不冗余存储，
 * 渲染时 JOIN sys_menu 实时取。</p>
 *
 * @author Ethan
 * @date 2026/06/05
 */
@Getter
@Setter
@TableName("user_quick_nav")
public class UserQuickNavEntity extends BaseEntity {

    private Long userId;

    private Long menuId;

    private Integer sortOrder;

    private Integer enabled;
}
