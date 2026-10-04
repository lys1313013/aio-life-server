package top.aiolife.mcp.api;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.mcp.invoker.McpToolInvoker;
import top.aiolife.mcp.registry.McpToolRegistry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@AllArgsConstructor
@RequestMapping("/mcp")
public class McpController {

    private final McpToolRegistry toolRegistry;
    private final McpToolInvoker toolInvoker;

    @GetMapping("/tools")
    public ApiResponse<List<Map<String, Object>>> listTools() {
        var tools = toolRegistry.getAllTools().stream()
                .map(tool -> {
                    var mcpTool = tool.mcpTool();
                    Map<String, Object> info = new LinkedHashMap<>();
                    info.put("name", tool.name());
                    info.put("description", tool.description());
                    info.put("inputSchema", mcpTool.inputSchema());
                    return info;
                })
                .toList();
        return ApiResponse.success(tools);
    }

    @PostMapping("/tools/call")
    public ApiResponse<Map<String, Object>> callTool(@RequestBody ToolCallRequest request) {
        McpToolRegistry.RegisteredMcpTool tool = toolRegistry.getTool(request.name());
        if (tool == null) {
            return ApiResponse.error("工具不存在: " + request.name());
        }
        Map<String, Object> args = request.arguments() != null ? request.arguments() : Map.of();
        var result = toolInvoker.invoke(tool, args, null, null);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("content", result.content());
        resp.put("isError", result.isError());
        return ApiResponse.success(resp);
    }

    public record ToolCallRequest(String name, Map<String, Object> arguments) {}
}
