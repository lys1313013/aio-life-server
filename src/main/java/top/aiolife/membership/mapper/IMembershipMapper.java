package top.aiolife.membership.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Options;
import top.aiolife.membership.pojo.entity.MembershipRecordEntity;

/**
 * 会员 Mapper
 *
 * @author Lys
 * @date 2026/08/06
 */
@Mapper
public interface IMembershipMapper extends BaseMapper<MembershipRecordEntity> {
    @Select("SELECT * FROM membership_record WHERE id = #{id} AND user_id = #{userId} AND is_deleted = 0 FOR UPDATE")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    MembershipRecordEntity lockOwned(@Param("id") Long id, @Param("userId") long userId);
}
