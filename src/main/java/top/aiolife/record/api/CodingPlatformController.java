package top.aiolife.record.api;

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

    @GetMapping("/github/calendar")
    public ApiResponse<JsonNode> githubCalendar() {
        return ApiResponse.success(service.githubCalendar(StpUtil.getLoginIdAsLong()));
    }

    @GetMapping("/github/repos")
    public ApiResponse<JsonNode> githubRepos(@RequestParam(value = "page", defaultValue = "1") int page) {
        return ApiResponse.success(service.githubRepos(StpUtil.getLoginIdAsLong(), page));
    }

    @GetMapping("/github/repository/{section}")
    public ApiResponse<JsonNode> githubRepository(@PathVariable String section, @RequestParam("name") String name) {
        return ApiResponse.success(service.githubRepository(StpUtil.getLoginIdAsLong(), name, section));
    }

    @GetMapping("/leetcode/data/{section}")
    public ApiResponse<JsonNode> leetcode(@PathVariable String section) {
        return ApiResponse.success(service.leetcode(StpUtil.getLoginIdAsLong(), section));
    }
}
