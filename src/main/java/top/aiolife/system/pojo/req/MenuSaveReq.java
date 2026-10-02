package top.aiolife.system.pojo.req;

import java.util.Map;
import lombok.Data;

/**
 * 系统菜单保存请求体
 *
 * @author Ethan
 * @date 2026/04/19
 */
@Data
public class MenuSaveReq {

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

    private Integer status;
}

