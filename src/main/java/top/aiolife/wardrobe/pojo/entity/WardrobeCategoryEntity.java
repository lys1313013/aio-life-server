package top.aiolife.wardrobe.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import top.aiolife.core.pojo.entity.BaseEntity;

/**
 * 衣柜分类实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wardrobe_category")
public class WardrobeCategoryEntity extends BaseEntity {

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

    /**
     * 分类类型: 0=系统预设 1=用户自定义
     */
    private Integer categoryType;

    /**
     * 用户ID(用户自定义分类时)
     */
    private Long userId;
}
