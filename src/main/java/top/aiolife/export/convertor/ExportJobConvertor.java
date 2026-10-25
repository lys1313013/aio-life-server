package top.aiolife.export.convertor;

import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import top.aiolife.export.api.ExportAuditController.AuditView;
import top.aiolife.export.pojo.entity.ExportAuditEntity;
import top.aiolife.export.pojo.entity.ExportJobEntity;
import top.aiolife.export.pojo.vo.ExportJobVO;

/** 对外字段固定在 VO 中，避免将对象键、会话摘要等内部信息序列化。 */
@Mapper(builder = @Builder(disableBuilder = true))
public interface ExportJobConvertor {
    ExportJobConvertor INSTANCE = Mappers.getMapper(ExportJobConvertor.class);

    @Mapping(target = "rowCount", defaultValue = "0")
    ExportJobVO toVO(ExportJobEntity entity);

    AuditView toAuditVO(ExportAuditEntity entity);
}
