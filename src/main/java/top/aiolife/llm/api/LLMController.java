package top.aiolife.llm.api;

import cn.dev33.satoken.stp.StpUtil;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import top.aiolife.core.constant.ResponseCodeConst;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.llm.convertor.LlmApiConvertor;
import top.aiolife.llm.pojo.entity.ChatMessageEntity;
import top.aiolife.llm.pojo.req.ChatReq;
import top.aiolife.llm.pojo.req.ChatSessionSaveReq;
import top.aiolife.llm.pojo.vo.ChatMessageVO;
import top.aiolife.llm.pojo.vo.ConversationVO;
import top.aiolife.llm.service.ChatMessageService;
import top.aiolife.llm.service.ConversationService;
import top.aiolife.llm.service.LLMKeyService;
import top.aiolife.llm.service.LLMService;

@Slf4j
@RestController
@AllArgsConstructor
@RequestMapping("/llm")
public class LLMController {

    private final LLMService llmService;
    private final LLMKeyService llmKeyService;
    private final ChatMessageService chatMessageService;
    private final ConversationService chatSessionService;

    @PostMapping("/chat")
    public ApiResponse<String> chat(@Valid @RequestBody ChatReq request) {
        try {
            long userId = StpUtil.getLoginIdAsLong();
            String prompt = request.getPrompt();
            Long conversationId = request.getConversationId();

            var llmKey = llmKeyService.getDefaultLLMKey(userId);
            if (llmKey == null) {
                return ApiResponse.error(ResponseCodeConst.RSCODE_COMMON_FAIL, "请先配置大模型 API Key");
            }

            chatMessageService.saveMessage(userId, conversationId, "user", prompt, llmKey.getModelName());

            // 查询历史消息构建上下文
            String context = buildContext(userId, conversationId);
            String fullPrompt = context.isEmpty() ? prompt : context + "\n" + prompt;

            String response = llmService.generateResponse(
                    llmKey.getApiKey(),
                    llmKey.getBaseUrl(),
                    llmKey.getModelName(),
                    fullPrompt,
                    null
            );

            chatMessageService.saveMessage(userId, conversationId, "assistant", response, llmKey.getModelName());

            return ApiResponse.success(response);
        } catch (Exception e) {
            log.error("Failed to chat with LLM: {}", e.getMessage(), e);
            return ApiResponse.error(ResponseCodeConst.RSCODE_COMMON_FAIL, e.getMessage());
        }
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@Valid @RequestBody ChatReq request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");

        long userId = StpUtil.getLoginIdAsLong();
        String prompt = request.getPrompt();
        Long conversationId = request.getConversationId();

        SseEmitter emitter = new SseEmitter(300000L);
        StringBuilder fullResponse = new StringBuilder();

        try {
            var llmKey = llmKeyService.getDefaultLLMKey(userId);
            if (llmKey == null) {
                emitter.send(SseEmitter.event().data("{\"event\":\"error\",\"data\":\"请先配置大模型 API Key\"}"));
                emitter.complete();
                return emitter;
            }

            chatMessageService.saveMessage(userId, conversationId, "user", prompt, llmKey.getModelName());

            // 查询历史消息构建上下文
            String context = buildContext(userId, conversationId);
            String fullPrompt = context.isEmpty() ? prompt : context + "\n" + prompt;

            var streamingModel = llmService.getStreamingChatModel(
                    llmKey.getApiKey(),
                    llmKey.getBaseUrl(),
                    llmKey.getModelName()
            );

            String modelName = llmKey.getModelName();
            streamingModel.chat(fullPrompt, new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String token) {
                    try {
                        fullResponse.append(token);
                        emitter.send(SseEmitter.event().data(token));
                    } catch (IOException e) {
                        log.error("Failed to send token: {}", e.getMessage());
                        emitter.completeWithError(e);
                    }
                }

                @Override
                public void onCompleteResponse(ChatResponse response) {
                    try {
                        chatMessageService.saveMessage(userId, conversationId, "assistant", fullResponse.toString(), modelName);
                        emitter.send(SseEmitter.event().data("[DONE]"));
                        emitter.complete();
                    } catch (IOException e) {
                        log.error("Failed to send complete event: {}", e.getMessage());
                        emitter.completeWithError(e);
                    }
                }

                @Override
                public void onError(Throwable error) {
                    try {
                        emitter.send(SseEmitter.event().data("[ERROR] " + error.getMessage()));
                        emitter.complete();
                    } catch (IOException e) {
                        log.error("Failed to send error event: {}", e.getMessage());
                        emitter.completeWithError(e);
                    }
                }
            });
        } catch (Exception e) {
            log.error("Failed to start streaming chat: {}", e.getMessage(), e);
            try {
                emitter.send(SseEmitter.event().data("[ERROR] " + e.getMessage()));
            } catch (IOException ioException) {
                log.error("Failed to send error event: {}", ioException.getMessage());
            }
            emitter.complete();
        }

        emitter.onTimeout(() -> {
            log.warn("SSE emitter timeout");
            emitter.complete();
        });

        emitter.onCompletion(() -> {
            log.debug("SSE emitter completed");
        });

        return emitter;
    }

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
            return ApiResponse.error(ResponseCodeConst.RSCODE_COMMON_FAIL, e.getMessage());
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
            return ApiResponse.error(ResponseCodeConst.RSCODE_COMMON_FAIL, e.getMessage());
        }
    }

    @GetMapping("/sessions")
    public ApiResponse<List<ConversationVO>> getSessions() {
        try {
            long userId = StpUtil.getLoginIdAsLong();
            return ApiResponse.success(LlmApiConvertor.INSTANCE.toConversationVOList(chatSessionService.listByUserId(userId)));
        } catch (Exception e) {
            log.error("Failed to get chat sessions: {}", e.getMessage(), e);
            return ApiResponse.error(ResponseCodeConst.RSCODE_COMMON_FAIL, e.getMessage());
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
            return ApiResponse.error(ResponseCodeConst.RSCODE_COMMON_FAIL, e.getMessage());
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
            return ApiResponse.error(ResponseCodeConst.RSCODE_COMMON_FAIL, e.getMessage());
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
            return ApiResponse.error(ResponseCodeConst.RSCODE_COMMON_FAIL, e.getMessage());
        }
    }

    /**
     * 构建对话上下文，查询历史消息并格式化为 LLM 可读的字符串
     */
    private String buildContext(Long userId, Long conversationId) {
        if (conversationId == null) {
            return "";
        }
        // 查询最近 10 条历史消息
        List<ChatMessageEntity> history = chatMessageService.listByconversationId(userId, conversationId);
        if (history == null || history.isEmpty()) {
            return "";
        }
        // 只取最近 10 条
        int start = Math.max(0, history.size() - 10);
        List<ChatMessageEntity> recentHistory = history.subList(start, history.size());

        StringBuilder context = new StringBuilder();
        for (ChatMessageEntity msg : recentHistory) {
            String roleLabel = "user".equals(msg.getRole()) ? "User" : "AI";
            context.append(roleLabel).append(": ").append(msg.getContent()).append("\n");
        }
        return context.toString();
    }
}
