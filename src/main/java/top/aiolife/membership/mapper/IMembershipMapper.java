package top.aiolife.membership.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import top.aiolife.membership.pojo.entity.MembershipRecordEntity;

/**
 * 会员 Mapper
 *
 * @author Lys
 * @date 2026/08/06
 */
@Mapper
public interface IMembershipMapper extends BaseMapper<MembershipRecordEntity> {
}
