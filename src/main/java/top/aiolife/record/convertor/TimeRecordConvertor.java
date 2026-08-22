package top.aiolife.record.convertor;

import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Named;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import top.aiolife.record.pojo.entity.TimeRecordEntity;
import top.aiolife.record.pojo.req.TimeRecordReq;
import top.aiolife.record.pojo.vo.TimeRecordDateRangeVO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 类功能描述
 *
 * @author Lys
 * @date 2026-02-22 23:06
 */
@Mapper(builder = @Builder(disableBuilder = true))
public interface TimeRecordConvertor {

    TimeRecordConvertor INSTANCE = Mappers.getMapper(TimeRecordConvertor.class);

    @Mapping(target = "categoryId", qualifiedByName = "stringToLongNullable")
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

    @Mapping(target = "startTime", qualifiedByName = "minutesToLocalDateTime")
    @Mapping(target = "endTime", qualifiedByName = "minutesToLocalDateTime")
    @Mapping(target = "exercises", ignore = true)
    TimeRecordDateRangeVO toDateRangeVO(TimeRecordEntity entity);

    List<TimeRecordDateRangeVO> toDateRangeVOList(List<TimeRecordEntity> entities);

    @Named("minutesToLocalDateTime")
    default LocalDateTime minutesToLocalDateTime(Integer minutes) {
        if (minutes == null) {
            return null;
        }
        return LocalDateTime.of(1970, 1, 1, minutes / 60, minutes % 60);
    }
}
