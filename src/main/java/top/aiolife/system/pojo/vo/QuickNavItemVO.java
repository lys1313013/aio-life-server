package top.aiolife.system.pojo.vo;

import lombok.Data;

/**
 * 用户已保存的快捷导航项
 *
 * <p>展示字段冗余在响应里，避免前端二次 JOIN。</p>
 *
 * @author Ethan
 * @date 2026/06/05
 */
@Data
public class QuickNavItemVO {

    private Long menuId;

    private Integer sortOrder;

    private Integer isEnabled;

    private String title;

    private String icon;

    private String color;

    private String path;

    /** "self" / "blank" */
    private String target;
}
