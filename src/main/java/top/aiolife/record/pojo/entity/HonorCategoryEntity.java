package top.aiolife.record.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 荣誉分类实体
 *
 * @author Lys
 * @date 2026/04/11
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("honor_category")
public class HonorCategoryEntity extends BaseEntity {

    private Long userId;

    private String name;

    private String icon;

    private String color;

    private Integer sortOrder;

}
