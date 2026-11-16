package top.aiolife.wardrobe.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import top.aiolife.core.pojo.entity.BaseEntity;

/**
 * 衣柜分类实体
 */
@Getter
@Setter
@ToString
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
    private Integer sortOrder;

    /**
     * 用户ID；NULL表示系统预设分类
     */
    private Long userId;
}
