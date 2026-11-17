package top.aiolife.wardrobe.pojo.req;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import top.aiolife.core.id.AttachmentKey;
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
     * 适用季节数组:春、夏、秋、冬
     */
    @Size(max = 4, message = "适用季节最多选择四项")
    private List<@Pattern(regexp = "春|夏|秋|冬", message = "适用季节只能为春、夏、秋、冬") String> season;

    /**
     * 购买日期
     */
    private LocalDate purchaseDate;

    /**
     * 购入价格
     */
    private BigDecimal purchasePrice;

    /**
     * 图片文件ID
     */
    @Pattern(regexp = AttachmentKey.PATTERN)
    private String fileId;

    /**
     * 尺码
     */
    private String size;

    /**
     * 备注
     */
    private String remark;
}
