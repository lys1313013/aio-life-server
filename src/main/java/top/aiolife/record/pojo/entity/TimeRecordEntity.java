package top.aiolife.record.pojo.entity;


import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;
import top.aiolife.core.pojo.entity.AuditEntity;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("time_record")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TimeRecordEntity extends AuditEntity {

    private Long userId;

    /**
     * 分类id
     */
    private Long categoryId;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;

    /**
     * 开始时间 （分钟）包含开始时间
     */
    private Integer startTime;
    /**
     * 结束时间 （分钟） 包含结束时间
     */
    private Integer endTime;
    private String title;
    private String description;
    private Integer duration;

    /**
     * 关联业务类型，对应 RelateTypeEnum
     */
    private Integer relateType;

    /**
     * 关联业务ID
     */
    private Long relateId;

    /**
     * 是否手动添加
     */
    private Long isManual;

    @TableId
    private String id;

    @TableLogic
    private Integer isDeleted;
}
