package top.aiolife.record.pojo.entity;

import top.aiolife.core.pojo.entity.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.OptBoolean;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * 类功能描述
 *
 * @author Lys
 * @date 2026-02-07 16:31
 */
@Getter
@Setter
@TableName("milestone")
public class MilestoneEntity extends BaseEntity {
    private Long userId;

    private String title;

    private String description;

    /** 开始日期，精确到日。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, lenient = OptBoolean.FALSE)
    private LocalDate date;

    /** 结束日期，未记录时为空；映射 milestone.end_date。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, lenient = OptBoolean.FALSE)
    private LocalDate endDate;

    private String type;

    private String tags;
}
