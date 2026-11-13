package top.aiolife.record.api;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.aiolife.core.id.IdCodec;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.web.CurrentUserId;
import top.aiolife.record.service.CodingPlatformService;

/**
 * 力扣数据控制器
 *
 * @author Lys
 * @date 2025/04/10 00:05
 */
@Tag(name = "LeetCode")
@RestController
@AllArgsConstructor
@RequestMapping("/leetcode")
@SaCheckLogin
public class LeetcodeController {

    private final CodingPlatformService codingPlatformService;

    @Operation(summary = "查询力扣指定数据")
    @GetMapping("/data/{section}")
    public ApiResponse<JsonNode> leetcode(
            @CurrentUserId String userId,
            @Parameter(description = "数据项：profile、contest、calendar、daily、recent") @PathVariable String section) {
        return ApiResponse.success(codingPlatformService.leetcode(IdCodec.decode(userId), section));
    }
}
