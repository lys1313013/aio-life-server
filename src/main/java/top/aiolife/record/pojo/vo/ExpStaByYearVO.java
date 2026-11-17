package top.aiolife.record.pojo.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 类功能描述
 *
 * @author Lys
 * @date 2025/10/06 00:25
 */
@Data
public class ExpStaByYearVO {

    private Integer year;

    private Integer month;

    private BigDecimal bookkeepingAmount;

    private Long typeId;

    private String typeName;
}
