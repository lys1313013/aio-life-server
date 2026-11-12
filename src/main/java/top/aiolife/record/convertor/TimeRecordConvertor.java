package top.aiolife.record.convertor;

import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Named;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;
import top.aiolife.record.pojo.entity.TimeRecordEntity;
import top.aiolife.record.pojo.req.TimeRecordReq;
import top.aiolife.record.pojo.vo.TimeRecordDateRangeVO;

import java.util.List;

/**
 * 类功能描述
 *
 * @author Lys
 * @date 2026-02-22 23:06
 */
@Mapper(builder = @Builder(disableBuilder = true), unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface TimeRecordConvertor {

    TimeRecordConvertor INSTANCE = Mappers.getMapper(TimeRecordConvertor.class);

    @Mapping(target = "categoryId", qualifiedByName = "stringToLongNullable")
    // 时长由服务端计算；归属、审计和删除状态由新增/更新业务分别控制。
    @Mapping(target = "durationMinutes", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "createUser", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateUser", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "isManual", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    TimeRecordEntity Req2Entity(TimeRecordReq timeRecordReq);

    @Named("stringToLongNullable")
    default Long stringToLongNullable(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Mapping(target = "categoryName", ignore = true)
    @Mapping(target = "exercises", ignore = true)
    TimeRecordDateRangeVO toDateRangeVO(TimeRecordEntity entity);

    List<TimeRecordDateRangeVO> toDateRangeVOList(List<TimeRecordEntity> entities);
}
