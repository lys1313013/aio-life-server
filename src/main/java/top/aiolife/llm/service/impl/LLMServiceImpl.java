package top.aiolife.llm.service.impl;

import java.time.Duration;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import top.aiolife.llm.service.LLMService;

@Slf4j
@Service
public class LLMServiceImpl implements LLMService {

    @Override
    public ChatModel getChatModel(String apiKey, String baseUrl, String modelName) {
        try {
            return OpenAiChatModel.builder()
                    .apiKey(apiKey)
                    .baseUrl(baseUrl)
                    .modelName(modelName)
                    .timeout(Duration.ofSeconds(300))
                    .logRequests(true)
                    .logResponses(true)
                    .build();
        } catch (Exception e) {
            log.error("Failed to initialize chat model: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize chat model", e);
        }
    }

    @Override
    public StreamingChatModel getStreamingChatModel(String apiKey, String baseUrl, String modelName) {
        try {
            return OpenAiStreamingChatModel.builder()
                    .apiKey(apiKey)
                    .baseUrl(baseUrl)
                    .modelName(modelName)
                    .timeout(Duration.ofSeconds(300))
                    .logRequests(true)
                    .logResponses(true)
                    .build();
        } catch (Exception e) {
            log.error("Failed to initialize streaming chat model: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize streaming chat model", e);
        }
    }

    @Override
    public String generateResponse(String apiKey, String baseUrl, String modelName, String prompt, String context) {
        try {
            ChatModel model = getChatModel(apiKey, baseUrl, modelName);
            String fullPrompt = context != null && !context.isEmpty() ? context + "\n" + prompt : prompt;
            return model.chat(fullPrompt);
        } catch (Exception e) {
            log.error("Failed to generate response: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate response", e);
        }
    }
}
