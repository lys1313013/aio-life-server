package top.aiolife.wardrobe.pojo.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 衣物 VO
 */
@Data
public class WardrobeItemVO {

    private Long id;

    private String name;

    private Long categoryId;

    private String categoryName;

    private String color;

    private String brand;

    private String season;

    private LocalDate purchaseDate;

    private BigDecimal price;

    private String fileId;

    private String size;

    private String remark;

    private LocalDateTime createTime;
}
