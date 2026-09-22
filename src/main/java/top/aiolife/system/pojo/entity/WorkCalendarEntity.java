package top.aiolife.system.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 全局工作日历基础数据，以日期为自然主键，不使用业务实体的 ID 和逻辑删除字段。
 */
@Getter
@Setter
@TableName("sys_work_calendar")
public class WorkCalendarEntity {
    @TableId(type = IdType.INPUT)
    private LocalDate calendarDate;
    private Integer dayType;
    private String holidayName;
    private String source;
    private LocalDateTime updatedAt;
}
