package top.aiolife.membership.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Options;
import top.aiolife.membership.pojo.entity.MembershipRecordEntity;
import top.aiolife.membership.pojo.dto.MembershipStatsAggregate;

/**
 * 会员 Mapper
 *
 * @author Lys
 * @date 2026/08/06
 */
@Mapper
public interface IMembershipMapper extends BaseMapper<MembershipRecordEntity> {
    /** 到期状态包含尚未开始的记录；月均成本仅合计已经开始且未过期的记录。 */
    @Select("""
            SELECT COUNT(CASE WHEN expiry_date > #{remindDate} THEN 1 END) AS activeCount,
                   COUNT(CASE WHEN expiry_date BETWEEN #{today} AND #{remindDate} THEN 1 END) AS expiringCount,
                   COUNT(CASE WHEN expiry_date < #{today} THEN 1 END) AS expiredCount,
                   COUNT(CASE WHEN expiry_date BETWEEN #{today} AND #{monthEnd} THEN 1 END) AS expiringThisMonthCount,
                   COALESCE(SUM(CASE WHEN expiry_date >= #{today}
                                     AND (start_date IS NULL OR start_date <= #{today})
                                     THEN monthly_amount ELSE 0 END), 0) AS monthlyAmount
            FROM membership_record
            WHERE user_id = #{userId} AND is_deleted = 0
            """)
    MembershipStatsAggregate aggregateStats(@Param("userId") long userId,
                                            @Param("today") LocalDate today,
                                            @Param("remindDate") LocalDate remindDate,
                                            @Param("monthEnd") LocalDate monthEnd);

    @Select("SELECT * FROM membership_record WHERE id = #{id} AND user_id = #{userId} AND is_deleted = 0 FOR UPDATE")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    MembershipRecordEntity lockOwned(@Param("id") Long id, @Param("userId") long userId);
}
