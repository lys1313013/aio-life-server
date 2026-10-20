package top.aiolife.record.convertor;

import org.mapstruct.BeanMapping;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;
import top.aiolife.record.pojo.entity.BodyProfileEntity;
import top.aiolife.record.pojo.req.BodyProfileSaveReq;
import top.aiolife.record.pojo.vo.BodyProfileVO;

@Mapper(builder = @Builder(disableBuilder = true), unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface BodyProfileConvertor {
    BodyProfileConvertor INSTANCE = Mappers.getMapper(BodyProfileConvertor.class);

    @BeanMapping(ignoreUnmappedSourceProperties = {"version"})
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "createUser", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateUser", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    BodyProfileEntity toEntity(BodyProfileSaveReq request);

    @Mapping(target = "bmi", ignore = true)
    BodyProfileVO toVO(BodyProfileEntity entity);
}
