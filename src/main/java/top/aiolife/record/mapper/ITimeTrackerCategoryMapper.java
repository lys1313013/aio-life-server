package top.aiolife.record.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import top.aiolife.record.pojo.entity.TimeTrackerCategoryEntity;

/**
 * 时间追踪-分类配置表(TimeTrackerCategory) Mapper 接口
 *
 * @author Lys1313013
 * @since 2026-03-07
 */
@Mapper
public interface ITimeTrackerCategoryMapper extends BaseMapper<TimeTrackerCategoryEntity> {
}
