package top.aiolife.mcp.registry;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import top.aiolife.mcp.adapter.LangChain4jToolSchemaAdapter;
import top.aiolife.mcp.annotation.McpToolProvider;

import jakarta.annotation.PostConstruct;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * MCP 工具注册表
 *
 * @author Lys
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpToolRegistry {

    private final ApplicationContext applicationContext;
    private final LangChain4jToolSchemaAdapter schemaAdapter;

    private final Map<String, RegisteredMcpTool> registeredTools = new LinkedHashMap<>();

    @PostConstruct
    public void init() {
        Map<String, Object> toolProviders = applicationContext.getBeansWithAnnotation(McpToolProvider.class);
        toolProviders.values().forEach(this::registerTools);
        log.info("MCP 工具注册完成，共 {} 个", registeredTools.size());
    }

    public Collection<RegisteredMcpTool> getAllTools() {
        return registeredTools.values();
    }

    public RegisteredMcpTool getTool(String name) {
        return registeredTools.get(name);
    }

    private void registerTools(Object bean) {
        Class<?> targetClass = AopUtils.getTargetClass(Objects.requireNonNull(bean));
        for (Method method : targetClass.getDeclaredMethods()) {
            if (method.getAnnotation(Tool.class) == null) {
                continue;
            }
            if (method.getParameterCount() > 1) {
                throw new IllegalStateException("MCP 工具目前最多支持单个入参方法: " + method);
            }
            ToolSpecification toolSpecification = ToolSpecifications.toolSpecificationFrom(method);
            String toolName = toolSpecification.name();
            String description = toolSpecification.description();
            McpSchema.Tool mcpTool = schemaAdapter.toMcpTool(toolName, description, method, toolSpecification);
            RegisteredMcpTool registeredMcpTool = new RegisteredMcpTool(
                    toolName,
                    description,
                    bean,
                    method,
                    method.getParameterCount() > 0 ? method.getParameterTypes()[0] : null,
                    toolSpecification,
                    mcpTool
            );
            RegisteredMcpTool existing = registeredTools.putIfAbsent(toolName, registeredMcpTool);
            if (existing != null) {
                throw new IllegalStateException("MCP 工具名称重复: " + toolName);
            }
            log.info("注册 MCP 工具: {} -> {}.{}", toolName, targetClass.getSimpleName(), method.getName());
        }
    }

    /**
     * 已注册的 MCP 工具
     */
    public record RegisteredMcpTool(
            String name,
            String description,
            Object bean,
            Method method,
            Class<?> inputType,
            ToolSpecification toolSpecification,
            McpSchema.Tool mcpTool) {
    }
}
