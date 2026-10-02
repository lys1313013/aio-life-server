package top.aiolife.system.pojo.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 后端菜单路由返回对象
 *
 * @author Ethan
 * @date 2026/04/19
 */
@Data
public class MenuRouteVO {

    private Long id;

    private String path;

    private String name;

    /** 图标颜色，六位十六进制；为空时使用默认颜色。 */
    private String iconColor;

    private String component;

    private String redirect;

    private Map<String, Object> meta;

    private List<MenuRouteVO> children;
}

