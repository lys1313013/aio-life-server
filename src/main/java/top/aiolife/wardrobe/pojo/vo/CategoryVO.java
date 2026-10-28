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

    private Integer categoryType;

    private List<CategoryVO> children;
}
