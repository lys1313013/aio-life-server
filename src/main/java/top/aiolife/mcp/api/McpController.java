package top.aiolife.mcp.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import top.aiolife.core.constant.ResponseCodeConst;
import top.aiolife.core.exception.ApiException;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.mcp.invoker.McpToolInvoker;
import top.aiolife.mcp.registry.McpToolRegistry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Tag(name = "MCP")
@RestController
@AllArgsConstructor
@RequestMapping("/mcp")
public class McpController {

    private final McpToolRegistry toolRegistry;
    private final McpToolInvoker toolInvoker;

    @Operation(summary = "查询 MCP 工具及参数定义")
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

    @Operation(summary = "调用 MCP 工具")
    @PostMapping("/tools/call")
    public ApiResponse<Map<String, Object>> callTool(@Valid @RequestBody ToolCallRequest request) {
        McpToolRegistry.RegisteredMcpTool tool = toolRegistry.getTool(request.name());
        if (tool == null) {
            throw new ApiException(ResponseCodeConst.COMMON_FAIL, "工具不存在: " + request.name());
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
