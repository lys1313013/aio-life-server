package top.aiolife.mcp.config;

import java.util.HashMap;
import java.util.Map;
import cn.dev33.satoken.stp.StpUtil;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.WebMvcStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;
import top.aiolife.mcp.handler.McpToolHandlers;
import top.aiolife.sso.util.SecondaryLockSession;

/**
 * MCP Server 配置
 *
 * @author Lys
 * @date 2026/04/25
 */
@Configuration
public class McpServerConfig {

    public static final String LOGIN_ID_CONTEXT_KEY = "loginId";
    public static final String SESSION_HASH_CONTEXT_KEY = "secondaryLockSessionHash";

    @Bean
    public WebMvcStreamableServerTransportProvider webMvcStreamableServerTransportProvider(McpJsonConfig mcpJsonConfig) {
        return WebMvcStreamableServerTransportProvider.builder()
                .mcpEndpoint("/mcp")
                .jsonMapper(mcpJsonConfig.get())
                .contextExtractor(serverRequest -> {
                    Map<String, Object> context = new HashMap<>();
                    Object loginId = StpUtil.getLoginIdDefaultNull();
                    context.put(LOGIN_ID_CONTEXT_KEY, loginId);
                    if (loginId != null && !StpUtil.isSwitch()) {
                        context.put(SESSION_HASH_CONTEXT_KEY, SecondaryLockSession.currentHash());
                    }
                    return McpTransportContext.create(context);
                })
                .build();
    }

    @Bean(destroyMethod = "close")
    public McpSyncServer mcpSyncServer(WebMvcStreamableServerTransportProvider transportProvider,
                                       McpToolHandlers mcpToolHandlers) {
        return McpServer.sync(transportProvider)
                .serverInfo("aio-life-server-mcp", "1.0.0")
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .build())
                .tools(mcpToolHandlers.getToolSpecifications())
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> mcpRouterFunction(WebMvcStreamableServerTransportProvider transportProvider,
                                                            McpSyncServer mcpSyncServer) {
        return transportProvider.getRouterFunction();
    }
}
