package top.aiolife.system.pojo.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 菜单管理端返回对象
 *
 * @author Ethan
 * @date 2026/04/19
 */
@Data
public class MenuAdminVO {

    private Long id;

    private Long parentId;

    private String name;

    private String path;

    /** 图标颜色，六位十六进制；为空时使用默认颜色。 */
    private String iconColor;

    private String component;

    private String redirect;

    private Map<String, Object> meta;

    private String roles;

    private Integer sort;

    /** Web 端启用状态。 */
    private Integer status;

    /** 移动端启用状态。 */
    private Integer mobileStatus;

    private List<MenuAdminVO> children;
}

