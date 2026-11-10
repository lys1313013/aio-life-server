package top.aiolife.record.mapper;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import top.aiolife.record.pojo.vo.FinanceCalendarVO;

/** 在数据库聚合，不加载或分页扫描账目。 */
public interface FinanceCalendarMapper {
    @Select("""
        <script>
        SELECT MIN(CAST(exp_time AS DATE)) AS date, COALESCE(SUM(amt), 0) AS amount
        FROM expense
        WHERE user_id = #{userId} AND is_deleted = 0
        <if test="start != null">AND exp_time &gt;= #{start}</if>
        <if test="end != null">AND exp_time &lt; #{end}</if>
        GROUP BY
        <choose>
            <when test="granularity == 'year'">EXTRACT(YEAR FROM exp_time)</when>
            <when test="granularity == 'month'">EXTRACT(YEAR FROM exp_time), EXTRACT(MONTH FROM exp_time)</when>
            <otherwise>EXTRACT(YEAR FROM exp_time), EXTRACT(MONTH FROM exp_time), EXTRACT(DAY FROM exp_time)</otherwise>
        </choose>
        ORDER BY date
        </script>
        """)
    List<FinanceCalendarVO.Point> aggregate(@Param("userId") long userId,
                                            @Param("start") LocalDateTime start,
                                            @Param("end") LocalDateTime end,
                                            @Param("granularity") String granularity);

    @Select("""
        <script>
        SELECT MIN(CAST(inc_date AS DATE)) AS date, COALESCE(SUM(amt), 0) AS amount
        FROM income
        WHERE user_id = #{userId} AND is_deleted = 0
        <if test="start != null">AND inc_date &gt;= #{start}</if>
        <if test="end != null">AND inc_date &lt; #{end}</if>
        GROUP BY
        <choose>
            <when test="granularity == 'year'">EXTRACT(YEAR FROM inc_date)</when>
            <when test="granularity == 'month'">EXTRACT(YEAR FROM inc_date), EXTRACT(MONTH FROM inc_date)</when>
            <otherwise>EXTRACT(YEAR FROM inc_date), EXTRACT(MONTH FROM inc_date), EXTRACT(DAY FROM inc_date)</otherwise>
        </choose>
        ORDER BY date
        </script>
        """)
    List<FinanceCalendarVO.Point> aggregateIncome(@Param("userId") long userId,
                                            @Param("start") LocalDate start,
                                            @Param("end") LocalDate end,
                                            @Param("granularity") String granularity);
}
