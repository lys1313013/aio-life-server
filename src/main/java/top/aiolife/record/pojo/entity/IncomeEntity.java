package top.aiolife.record.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 类功能描述
 *
 * @author Lys
 * @date 2025/09/14 21:05
 */
@Getter
@Setter
@TableName("income")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IncomeEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private BigDecimal amt;
    /**
     * 收入时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate incDate;
    private String remark;
    private Long userId;
    private Long createUser;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    private Long updateUser;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
    private Integer isDeleted;
    /**
     * 收入类型ID
     */
    private Long incTypeId;
    private BigDecimal tax;
}
