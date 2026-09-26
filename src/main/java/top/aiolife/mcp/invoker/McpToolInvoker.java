package top.aiolife.mcp.invoker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import top.aiolife.mcp.auth.McpSaTokenScope;
import top.aiolife.mcp.registry.McpToolRegistry;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;

/**
 * MCP 工具调用器
 *
 * @author Lys
 */
@Component
@RequiredArgsConstructor
public class McpToolInvoker {

    private final ObjectMapper objectMapper;
    private final top.aiolife.sso.service.SecondaryLockGuard secondaryLockGuard;

    public McpSchema.CallToolResult invoke(McpToolRegistry.RegisteredMcpTool tool,
                                           Map<String, Object> arguments,
                                           Object loginId,
                                           RequestAttributes requestAttributes) {
        try {
            Object result = McpSaTokenScope.runWithContext(loginId, requestAttributes, () -> {
                secondaryLockGuard.checkTool(tool.name());
                return invokeTool(tool, arguments);
            });
            return McpSchema.CallToolResult.builder()
                    .addTextContent(toJson(result))
                    .isError(false)
                    .build();
        } catch (InvocationTargetException exception) {
            Throwable targetException = exception.getTargetException();
            return errorResult(targetException == null ? exception.getMessage() : targetException.getMessage());
        } catch (Exception exception) {
            return errorResult(exception.getMessage());
        }
    }

    private Object invokeTool(McpToolRegistry.RegisteredMcpTool tool,
                              Map<String, Object> arguments) throws IllegalAccessException, InvocationTargetException {
        tool.method().setAccessible(true);
        if (tool.method().getParameterCount() == 0) {
            return tool.method().invoke(tool.bean());
        }
        Object input = objectMapper.convertValue(arguments, tool.inputType());
        return tool.method().invoke(tool.bean(), input);
    }

    private String toJson(Object result) throws JsonProcessingException {
        if (result instanceof String value) {
            return value;
        }
        return objectMapper.writeValueAsString(result);
    }

    private McpSchema.CallToolResult errorResult(String errorMessage) {
        String safeMessage = errorMessage == null ? "工具执行失败" : errorMessage;
        return McpSchema.CallToolResult.builder()
                .addTextContent(safeMessage)
                .isError(true)
                .build();
    }
}
