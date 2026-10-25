package top.aiolife.scheduling.convertor;

import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import top.aiolife.scheduling.pojo.entity.ScheduledJobEntity;
import top.aiolife.scheduling.pojo.entity.ScheduledJobLogEntity;
import top.aiolife.scheduling.pojo.vo.ScheduledJobVO;
import top.aiolife.scheduling.pojo.vo.ScheduledJobLogVO;

@Mapper(builder = @Builder(disableBuilder = true))
public interface ScheduledJobConvertor {
    ScheduledJobConvertor INSTANCE = Mappers.getMapper(ScheduledJobConvertor.class);
    @Mapping(target = "schedulerEnabled", ignore = true)
    @Mapping(target = "nextExecutionTime", ignore = true)
    ScheduledJobVO toVO(ScheduledJobEntity entity);
    ScheduledJobLogVO toLogVO(ScheduledJobLogEntity entity);
}
