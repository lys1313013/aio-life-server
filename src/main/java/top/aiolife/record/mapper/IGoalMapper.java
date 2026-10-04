package top.aiolife.record.mapper;

import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import top.aiolife.record.pojo.entity.GoalEntity;

/**
 * 目标管理 Mapper 接口
 *
 * @author Lys
 * @date 2026-04-05
 */
public interface IGoalMapper extends BaseMapper<GoalEntity> {
    /** 锁定用户行，将同一用户的固定、排序和删除串行化；即使尚无固定记录也有效。 */
    @Select("SELECT id FROM `user` WHERE id = #{userId} AND is_deleted = 0 FOR UPDATE")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    Long lockPinOwner(@Param("userId") long userId);
}
