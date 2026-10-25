package top.aiolife.scheduling.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import top.aiolife.scheduling.pojo.entity.ScheduledJobEntity;

@Mapper
public interface ScheduledJobMapper extends BaseMapper<ScheduledJobEntity> {
}
