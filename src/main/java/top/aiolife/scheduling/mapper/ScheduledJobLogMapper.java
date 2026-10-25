package top.aiolife.scheduling.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import top.aiolife.scheduling.pojo.entity.ScheduledJobLogEntity;

@Mapper
public interface ScheduledJobLogMapper extends BaseMapper<ScheduledJobLogEntity> {
}
