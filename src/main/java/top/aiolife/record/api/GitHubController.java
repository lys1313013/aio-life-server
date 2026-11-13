package top.aiolife.record.api;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.aiolife.core.id.IdCodec;
import top.aiolife.core.query.QueryParams;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.web.CurrentUserId;
import top.aiolife.record.pojo.query.GithubCommitQuery;
import top.aiolife.record.pojo.vo.GithubCommitVO;
import top.aiolife.record.service.CodingPlatformService;
import top.aiolife.record.service.IGithubService;

/**
 * GitHub 相关接口（前端代理 GitHub API，规避浏览器 CORS）
 *
 * @author Lys
 * @date 2025/04/28 22:15
 */
@Tag(name = "GitHub")
@RestController
@AllArgsConstructor
@RequestMapping("/github")
@SaCheckLogin
public class GitHubController {

    private final IGithubService githubService;

    private final CodingPlatformService codingPlatformService;

    @Operation(summary = "查询 GitHub 最近提交")
    @GetMapping("/recent-commits")
    public ApiResponse<List<GithubCommitVO>> queryRecentCommits(@CurrentUserId String userId,
                                                                @QueryParams GithubCommitQuery query) {
        return ApiResponse.success(githubService.searchRecentCommitsForUser(
                IdCodec.decode(userId), query.getPage(), query.getPageSize()));
    }

    @Operation(summary = "查询 GitHub 贡献日历")
    @GetMapping("/calendar")
    public ApiResponse<JsonNode> githubCalendar(@CurrentUserId String userId) {
        return ApiResponse.success(codingPlatformService.githubCalendar(IdCodec.decode(userId)));
    }

    @Operation(summary = "查询 GitHub 仓库列表")
    @GetMapping("/repos")
    public ApiResponse<JsonNode> githubRepos(@CurrentUserId String userId,
                                             @RequestParam(value = "page", defaultValue = "1") int page) {
        return ApiResponse.success(codingPlatformService.githubRepos(IdCodec.decode(userId), page));
    }

    @Operation(summary = "查询 GitHub 仓库指定数据")
    @GetMapping("/repository/{section}")
    public ApiResponse<JsonNode> githubRepository(
            @CurrentUserId String userId,
            @Parameter(description = "数据项：info、contributors") @PathVariable String section,
            @Parameter(description = "仓库名，不含账号前缀") @RequestParam("name") String name) {
        return ApiResponse.success(codingPlatformService.githubRepository(
                IdCodec.decode(userId), name, section));
    }
}
