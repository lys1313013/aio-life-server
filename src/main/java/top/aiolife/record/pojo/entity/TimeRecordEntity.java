package top.aiolife.record.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import top.aiolife.core.pojo.entity.AuditEntity;

@Getter
@Setter
@ToString
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
     * 开始分钟（当天 0:00 起的分钟数，0–1439，包含该分钟）
     */
    private Integer startMinute;
    /**
     * 结束分钟（当天 0:00 起的分钟数，0–1439，包含该分钟）
     */
    private Integer endMinute;
    private String title;
    private String description;
    /**
     * 时长（分钟），闭区间 endMinute - startMinute + 1，由服务端计算
     */
    private Integer durationMinutes;

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
    private Integer isManual;

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    @TableLogic
    private Integer isDeleted;

    @Override
    protected Object identityKey() {
        return id;
    }
}
