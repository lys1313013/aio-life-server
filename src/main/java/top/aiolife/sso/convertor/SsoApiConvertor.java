package top.aiolife.sso.convertor;

import com.baomidou.mybatisplus.core.metadata.IPage;
import java.util.List;
import org.mapstruct.*;
import org.mapstruct.factory.Mappers;
import top.aiolife.core.resq.PageResp;
import top.aiolife.sso.pojo.entity.ApiKeyEntity;
import top.aiolife.sso.pojo.entity.MessageEntity;
import top.aiolife.sso.pojo.entity.UserEntity;
import top.aiolife.sso.pojo.req.MessageCreateReq;
import top.aiolife.sso.pojo.req.UserCreateReq;
import top.aiolife.sso.pojo.req.UserUpdateReq;
import top.aiolife.sso.pojo.vo.ApiKeyCreatedVO;
import top.aiolife.sso.pojo.vo.MessageVO;

/** 接口模型与持久化模型的显式转换，响应仅暴露 VO 声明的字段。 */
@Mapper(builder = @Builder(disableBuilder = true), unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface SsoApiConvertor {
    SsoApiConvertor INSTANCE = Mappers.getMapper(SsoApiConvertor.class);

    MessageVO toMessageVO(MessageEntity entity);
    List<MessageVO> toMessageVOList(List<MessageEntity> entities);

    default PageResp<MessageVO> toMessageVOPage(PageResp<MessageEntity> page) {
        return page == null ? null : page.map(this::toMessageVO);
    }

    default PageResp<MessageVO> toMessageVOPage(IPage<MessageEntity> page) {
        return page == null ? null : PageResp.of(toMessageVOList(page.getRecords()), page.getTotal());
    }

    ApiKeyCreatedVO toApiKeyCreatedVO(ApiKeyEntity entity);
    List<ApiKeyCreatedVO> toApiKeyCreatedVOList(List<ApiKeyEntity> entities);

    default PageResp<ApiKeyCreatedVO> toApiKeyCreatedVOPage(PageResp<ApiKeyEntity> page) {
        return page == null ? null : page.map(this::toApiKeyCreatedVO);
    }

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    MessageEntity fromMessageCreateReq(MessageCreateReq request);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    UserEntity fromUserCreateReq(UserCreateReq request);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    UserEntity fromUserUpdateReq(UserUpdateReq request);

}
