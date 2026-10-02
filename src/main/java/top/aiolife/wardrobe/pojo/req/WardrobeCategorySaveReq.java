package top.aiolife.wardrobe.pojo.req;

import lombok.Data;

/**
 * 分类请求 DTO
 */
@Data
public class WardrobeCategorySaveReq {


    /**
     * 分类名称
     */
    private String name;

    /**
     * 图标
     */
    private String icon;

    /**
     * 父分类ID
     */
    private Long parentId;

    /**
     * 排序
     */
    private Integer sort;
}
