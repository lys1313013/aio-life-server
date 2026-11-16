package top.aiolife.wardrobe.pojo.vo;

import lombok.Data;

import java.util.List;

/**
 * 分类 VO
 */
@Data
public class CategoryVO {

    private Long id;

    private String name;

    private String icon;

    private Long parentId;

    private Integer sortOrder;

    /** 是否为系统预设分类；预设不可修改或删除。 */
    private Boolean preset;

    private List<CategoryVO> children;
}
