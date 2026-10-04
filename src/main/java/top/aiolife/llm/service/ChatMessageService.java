package top.aiolife.llm.service;

import com.baomidou.mybatisplus.extension.service.IService;
import top.aiolife.llm.pojo.entity.ChatMessageEntity;

import java.util.List;

public interface ChatMessageService extends IService<ChatMessageEntity> {

    List<ChatMessageEntity> listByUserId(Long userId);

    List<ChatMessageEntity> listByconversationId(Long userId, Long conversationId);

    void deleteByUserId(Long userId);

    void deleteByconversationId(Long userId, Long conversationId);
}
