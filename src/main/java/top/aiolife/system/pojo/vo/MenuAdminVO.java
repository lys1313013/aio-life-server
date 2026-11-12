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

    private Integer sortOrder;

    /** 菜单展示端位掩码：1=Web，2=移动端，3=两端，0=均不展示。 */
    private Integer visibleClients;

    private List<MenuAdminVO> children;
}

