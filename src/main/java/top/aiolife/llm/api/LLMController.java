package top.aiolife.llm.api;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.validation.Valid;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.aiolife.core.constant.ResponseCodeConst;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.llm.convertor.LlmApiConvertor;
import top.aiolife.llm.pojo.entity.ChatMessageEntity;
import top.aiolife.llm.pojo.req.ChatSessionSaveReq;
import top.aiolife.llm.pojo.vo.ChatMessageVO;
import top.aiolife.llm.pojo.vo.ConversationVO;
import top.aiolife.llm.service.ChatMessageService;
import top.aiolife.llm.service.ConversationService;

/** 历史聊天消息与会话管理，不提供模型调用。 */
@Slf4j
@RestController
@AllArgsConstructor
@RequestMapping("/llm")
public class LLMController {

    private final ChatMessageService chatMessageService;
    private final ConversationService chatSessionService;

    @GetMapping("/chat/history")
    public ApiResponse<List<ChatMessageVO>> getChatHistory(@RequestParam(required = false) Long conversationId) {
        try {
            long userId = StpUtil.getLoginIdAsLong();
            List<ChatMessageEntity> history;
            if (conversationId != null) {
                history = chatMessageService.listByconversationId(userId, conversationId);
            } else {
                history = chatMessageService.listByUserId(userId);
            }
            return ApiResponse.success(LlmApiConvertor.INSTANCE.toChatMessageVOList(history));
        } catch (Exception e) {
            log.error("Failed to get chat history: {}", e.getMessage(), e);
            return ApiResponse.error(ResponseCodeConst.COMMON_FAIL, e.getMessage());
        }
    }

    @DeleteMapping("/chat/history")
    public ApiResponse<Void> clearChatHistory(@RequestParam(required = false) Long conversationId) {
        try {
            long userId = StpUtil.getLoginIdAsLong();
            if (conversationId != null) {
                chatMessageService.deleteByconversationId(userId, conversationId);
            } else {
                chatMessageService.deleteByUserId(userId);
            }
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("Failed to clear chat history: {}", e.getMessage(), e);
            return ApiResponse.error(ResponseCodeConst.COMMON_FAIL, e.getMessage());
        }
    }

    @GetMapping("/sessions")
    public ApiResponse<List<ConversationVO>> getSessions() {
        try {
            long userId = StpUtil.getLoginIdAsLong();
            return ApiResponse.success(LlmApiConvertor.INSTANCE.toConversationVOList(chatSessionService.listByUserId(userId)));
        } catch (Exception e) {
            log.error("Failed to get chat sessions: {}", e.getMessage(), e);
            return ApiResponse.error(ResponseCodeConst.COMMON_FAIL, e.getMessage());
        }
    }

    @PostMapping("/sessions")
    public ApiResponse<ConversationVO> createSession(@Valid @RequestBody ChatSessionSaveReq request) {
        try {
            long userId = StpUtil.getLoginIdAsLong();
            String title = request.getTitle();
            return ApiResponse.success(LlmApiConvertor.INSTANCE.toConversationVO(chatSessionService.createSession(userId, title)));
        } catch (Exception e) {
            log.error("Failed to create chat session: {}", e.getMessage(), e);
            return ApiResponse.error(ResponseCodeConst.COMMON_FAIL, e.getMessage());
        }
    }

    @PutMapping("/sessions/{conversationId}")
    public ApiResponse<Void> updateSession(@PathVariable Long conversationId, @Valid @RequestBody ChatSessionSaveReq request) {
        try {
            long userId = StpUtil.getLoginIdAsLong();
            String title = request.getTitle();
            chatSessionService.updateTitle(userId, conversationId, title);
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("Failed to update chat session: {}", e.getMessage(), e);
            return ApiResponse.error(ResponseCodeConst.COMMON_FAIL, e.getMessage());
        }
    }

    @DeleteMapping("/sessions/{conversationId}")
    public ApiResponse<Void> deleteSession(@PathVariable Long conversationId) {
        try {
            long userId = StpUtil.getLoginIdAsLong();
            chatSessionService.deleteSession(userId, conversationId);
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("Failed to delete chat session: {}", e.getMessage(), e);
            return ApiResponse.error(ResponseCodeConst.COMMON_FAIL, e.getMessage());
        }
    }
}
