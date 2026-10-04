package top.aiolife.system.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import lombok.Getter;
import lombok.Setter;
import top.aiolife.core.pojo.entity.BaseEntity;

/**
 * 系统菜单实体
 *
 * @author Ethan
 * @date 2026/04/19
 */
@Getter
@Setter
@TableName("sys_menu")
public class SysMenuEntity extends BaseEntity {

    private Long parentId;

    private String name;

    private String path;

    /** 图标颜色，六位十六进制；为空时使用默认颜色。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String iconColor;

    private String component;

    private String redirect;

    private String meta;

    private String roles;

    private Integer sort;

    /** Web 端启用状态。 */
    private Integer status;

    /** 移动端启用状态。 */
    private Integer mobileStatus;
}

