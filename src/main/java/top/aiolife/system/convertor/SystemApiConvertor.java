package top.aiolife.system.convertor;

import java.util.List;
import org.mapstruct.*;
import org.mapstruct.factory.Mappers;
import top.aiolife.core.resq.PageResp;
import top.aiolife.system.pojo.entity.ActivityLogEntity;
import top.aiolife.system.pojo.vo.ActivityLogVO;

/** 接口模型与持久化模型的显式转换，响应仅暴露 VO 声明的字段。 */
@Mapper(builder = @Builder(disableBuilder = true), unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface SystemApiConvertor {
    SystemApiConvertor INSTANCE = Mappers.getMapper(SystemApiConvertor.class);

    ActivityLogVO toActivityLogVO(ActivityLogEntity entity);
    List<ActivityLogVO> toActivityLogVOList(List<ActivityLogEntity> entities);

    default PageResp<ActivityLogVO> toActivityLogVOPage(PageResp<ActivityLogEntity> page) {
        return page == null ? null : page.map(this::toActivityLogVO);
    }

}
