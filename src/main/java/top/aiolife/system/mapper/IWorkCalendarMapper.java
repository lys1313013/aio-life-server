package top.aiolife.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import top.aiolife.system.pojo.entity.WorkCalendarEntity;

import java.time.LocalDate;

/** 工作日历只读查询。 */
public interface IWorkCalendarMapper extends BaseMapper<WorkCalendarEntity> {
    /** 获取指定日期的类型，缺失时返回 null。 */
    @Select("SELECT day_type FROM sys_work_calendar WHERE calendar_date = #{date}")
    Integer selectDayType(@Param("date") LocalDate date);

    /** 查找严格早于目标日期的最近同类日。 */
    @Select("""
            SELECT calendar_date
            FROM sys_work_calendar
            WHERE calendar_date < #{date}
              AND ((#{workday} = TRUE AND day_type IN (0, 3))
                OR (#{workday} = FALSE AND day_type IN (1, 2)))
            ORDER BY calendar_date DESC
            LIMIT 1
            """)
    LocalDate selectPreviousComparableDate(@Param("date") LocalDate date,
                                           @Param("workday") boolean workday);

    /** 检查候选日期到目标日期之间是否存在未导入的日期（含起始，不含结束）。 */
    @Select("""
            SELECT COUNT(*) FROM sys_work_calendar
            WHERE calendar_date >= #{startDate} AND calendar_date < #{endDate}
            """)
    long countDatesBetween(@Param("startDate") LocalDate startDate,
                           @Param("endDate") LocalDate endDate);
}
