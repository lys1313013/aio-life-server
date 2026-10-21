package top.aiolife.record.convertor;

import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;
import top.aiolife.record.pojo.entity.LifeFootprintEntity;
import top.aiolife.record.pojo.req.LifeFootprintSaveReq;
import top.aiolife.record.pojo.vo.LifeFootprintVO;

@Mapper(builder = @Builder(disableBuilder = true), unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface LifeFootprintConvertor {
    LifeFootprintConvertor INSTANCE = Mappers.getMapper(LifeFootprintConvertor.class);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "createUser", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateUser", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    LifeFootprintEntity toEntity(LifeFootprintSaveReq request);

    LifeFootprintVO toVO(LifeFootprintEntity entity);
}
