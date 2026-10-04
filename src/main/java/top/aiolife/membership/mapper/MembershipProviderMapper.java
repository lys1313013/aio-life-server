package top.aiolife.membership.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import top.aiolife.membership.pojo.entity.MembershipProviderEntity;

@Mapper
public interface MembershipProviderMapper extends BaseMapper<MembershipProviderEntity> {
    @Select("SELECT * FROM membership_provider WHERE id = #{id} AND is_deleted = 0 FOR UPDATE")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    MembershipProviderEntity lockById(@Param("id") Long id);
}
