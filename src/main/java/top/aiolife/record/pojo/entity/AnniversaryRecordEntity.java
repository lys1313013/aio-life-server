package top.aiolife.record.pojo.entity;

import top.aiolife.core.pojo.entity.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;

/**
 * 纪念日记录实体
 *
 * @author Lys
 * @date 2026/04/18
 */
@Data
@TableName("anniversary_record")
public class AnniversaryRecordEntity extends BaseEntity {

    private Long userId;

    private String title;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate targetDate;

    /**
     * anniversary-纪念日(正数), countdown-倒数日(倒数)
     */
    private String type;

    private String note;

    private String color;

    private String icon;
}
