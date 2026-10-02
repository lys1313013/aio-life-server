package top.aiolife.wardrobe.pojo.req;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.Data;

/**
 * 衣物请求 DTO
 */
@Data
public class WardrobeItemSaveReq {


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
    private List<String> season;

    /**
     * 购买日期
     */
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
}
