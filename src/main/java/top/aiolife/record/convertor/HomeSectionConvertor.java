package top.aiolife.record.convertor;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import top.aiolife.record.pojo.entity.BVideoEntity;
import top.aiolife.record.pojo.entity.DeviceEntity;
import top.aiolife.record.pojo.entity.MemoEntity;
import top.aiolife.record.pojo.vo.HomeSectionVO;

@Mapper
public interface HomeSectionConvertor {
    HomeSectionConvertor INSTANCE = Mappers.getMapper(HomeSectionConvertor.class);

    @Mapping(target = "hiddenContent", defaultValue = "false")
    HomeSectionVO.Memo memo(MemoEntity source);

    @Mapping(target = "progressPercent", ignore = true)
    @Mapping(target = "resumeUrl", ignore = true)
    HomeSectionVO.Video video(BVideoEntity source);

    @Mapping(target = "usageDays", ignore = true)
    @Mapping(target = "dailyCost", ignore = true)
    HomeSectionVO.Device device(DeviceEntity source);
}
