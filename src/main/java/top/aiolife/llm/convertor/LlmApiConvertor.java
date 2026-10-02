package top.aiolife.llm.convertor;

import java.util.List;
import org.mapstruct.*;
import org.mapstruct.factory.Mappers;
import top.aiolife.core.resq.PageResp;
import top.aiolife.llm.pojo.entity.ChatMessageEntity;
import top.aiolife.llm.pojo.entity.ConversationEntity;
import top.aiolife.llm.pojo.entity.LLMKeyEntity;
import top.aiolife.llm.pojo.req.LLMKeyCreateReq;
import top.aiolife.llm.pojo.req.LLMKeyUpdateReq;
import top.aiolife.llm.pojo.vo.ChatMessageVO;
import top.aiolife.llm.pojo.vo.ConversationVO;
import top.aiolife.llm.pojo.vo.LLMKeyVO;

/** 接口模型与持久化模型的显式转换，响应仅暴露 VO 声明的字段。 */
@Mapper(builder = @Builder(disableBuilder = true), unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface LlmApiConvertor {
    LlmApiConvertor INSTANCE = Mappers.getMapper(LlmApiConvertor.class);

    ChatMessageVO toChatMessageVO(ChatMessageEntity entity);
    List<ChatMessageVO> toChatMessageVOList(List<ChatMessageEntity> entities);

    default PageResp<ChatMessageVO> toChatMessageVOPage(PageResp<ChatMessageEntity> page) {
        return page == null ? null : page.map(this::toChatMessageVO);
    }

    ConversationVO toConversationVO(ConversationEntity entity);
    List<ConversationVO> toConversationVOList(List<ConversationEntity> entities);

    default PageResp<ConversationVO> toConversationVOPage(PageResp<ConversationEntity> page) {
        return page == null ? null : page.map(this::toConversationVO);
    }

    @Mapping(target = "hasApiKey", expression = "java(entity.getApiKey() != null && !entity.getApiKey().isBlank())")
    LLMKeyVO toLLMKeyVO(LLMKeyEntity entity);
    List<LLMKeyVO> toLLMKeyVOList(List<LLMKeyEntity> entities);

    default PageResp<LLMKeyVO> toLLMKeyVOPage(PageResp<LLMKeyEntity> page) {
        return page == null ? null : page.map(this::toLLMKeyVO);
    }

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    LLMKeyEntity fromLLMKeyCreateReq(LLMKeyCreateReq request);

    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    LLMKeyEntity fromLLMKeyUpdateReq(LLMKeyUpdateReq request);

}
