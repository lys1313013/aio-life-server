package top.aiolife.record.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import top.aiolife.record.pojo.dto.ExerciseStatisticsDTO;
import top.aiolife.record.pojo.entity.ExerciseRecordEntity;

import java.time.LocalDate;
import java.util.List;

/**
 * 运动记录Mapper接口
 *
 * @author Lys
 * @date 2025-11-29 18:40
 */
public interface IExerciseRecordMapper extends BaseMapper<ExerciseRecordEntity> {
    @Select("""
            SELECT exercise_date FROM exercise_record
            WHERE user_id = #{userId} AND is_deleted = 0 AND exercise_type_id IS NOT NULL
              AND exercise_date <= #{lastDate}
            GROUP BY exercise_date ORDER BY exercise_date DESC LIMIT #{limit}
            """)
    List<LocalDate> selectDashboardDates(@Param("userId") Long userId,
                                       @Param("lastDate") LocalDate lastDate, @Param("limit") int limit);

    @Select("""
            <script>
            SELECT exercise_type_id, exercise_date, COALESCE(SUM(exercise_count), 0) AS exercise_count
            FROM exercise_record
            WHERE user_id = #{userId} AND is_deleted = 0 AND exercise_type_id IS NOT NULL
              AND exercise_date IN
              <foreach collection="dates" item="date" open="(" separator="," close=")">#{date}</foreach>
            GROUP BY exercise_type_id, exercise_date ORDER BY exercise_date DESC, exercise_type_id
            </script>
            """)
    List<ExerciseStatisticsDTO> selectDashboardTotals(@Param("userId") Long userId,
                                                     @Param("dates") List<LocalDate> dates);

    /** 每个类型独立取四个历史运动日，用 UNION ALL 一次查询，兼容不支持窗口函数的 MySQL。 */
    @Select("""
            <script>
            SELECT exercise_type_id, exercise_date, exercise_count FROM (
                <foreach collection="typeIds" item="typeId" separator=" UNION ALL ">
                (SELECT exercise_type_id, exercise_date, COALESCE(SUM(exercise_count), 0) AS exercise_count
                 FROM exercise_record
                 WHERE user_id = #{userId} AND is_deleted = 0
                   AND exercise_date &lt; #{beforeDate} AND exercise_type_id = #{typeId}
                 GROUP BY exercise_type_id, exercise_date
                 ORDER BY exercise_date DESC LIMIT 4)
                </foreach>
            ) history ORDER BY exercise_type_id, exercise_date DESC
            </script>
            """)
    List<ExerciseStatisticsDTO> selectDashboardHistory(@Param("userId") Long userId,
                                                       @Param("typeIds") List<Long> typeIds,
                                                       @Param("beforeDate") LocalDate beforeDate);
}
