package top.aiolife.system.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
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

    private String component;

    private String redirect;

    private String meta;

    private String roles;

    private Integer sort;

    private Integer status;
}

