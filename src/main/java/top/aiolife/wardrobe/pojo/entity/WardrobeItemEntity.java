package top.aiolife.wardrobe.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import top.aiolife.core.pojo.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 衣柜衣物实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wardrobe_item")
public class WardrobeItemEntity extends BaseEntity {

    /**
     * 衣物名称
     */
    private String name;

    /**
     * 分类ID
     */
    private Long categoryId;

    /**
     * 颜色
     */
    private String color;

    /**
     * 品牌
     */
    private String brand;

    /**
     * 适用季节:春,夏,秋,冬
     */
    private String season;

    /**
     * 购买日期
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate purchaseDate;

    /**
     * 价格
     */
    private BigDecimal price;

    /**
     * 图片文件ID
     */
    private String fileId;

    /**
     * 尺码
     */
    private String size;

    /**
     * 备注
     */
    private String memo;

    /**
     * 用户ID
     */
    private Long userId;
}
