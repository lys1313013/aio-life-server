package top.aiolife.record.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.record.service.CodingPlatformService;

/** 固定只读第三方数据接口，沿用 /github、/leetcode 的登录与菜单锁边界。 */
@RestController
@SaCheckLogin
@RequiredArgsConstructor
public class CodingPlatformController {
    private final CodingPlatformService service;

    @Operation(summary = "查询 GitHub 贡献日历")
    @GetMapping("/github/calendar")
    public ApiResponse<JsonNode> githubCalendar() {
        return ApiResponse.success(service.githubCalendar(StpUtil.getLoginIdAsLong()));
    }

    @Operation(summary = "查询 GitHub 仓库列表")
    @GetMapping("/github/repos")
    public ApiResponse<JsonNode> githubRepos(@RequestParam(value = "page", defaultValue = "1") int page) {
        return ApiResponse.success(service.githubRepos(StpUtil.getLoginIdAsLong(), page));
    }

    @Operation(summary = "查询 GitHub 仓库指定数据")
    @GetMapping("/github/repository/{section}")
    public ApiResponse<JsonNode> githubRepository(@Parameter(description = "数据项：info、contributors") @PathVariable String section,
            @Parameter(description = "仓库名，不含账号前缀") @RequestParam("name") String name) {
        return ApiResponse.success(service.githubRepository(StpUtil.getLoginIdAsLong(), name, section));
    }

    @Operation(summary = "查询力扣指定数据")
    @GetMapping("/leetcode/data/{section}")
    public ApiResponse<JsonNode> leetcode(@Parameter(description = "数据项：profile、contest、calendar、daily、recent") @PathVariable String section) {
        return ApiResponse.success(service.leetcode(StpUtil.getLoginIdAsLong(), section));
    }
}
