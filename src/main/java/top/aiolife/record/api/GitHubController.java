package top.aiolife.record.api;

import cn.dev33.satoken.stp.StpUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.query.QueryParams;
import top.aiolife.core.query.PaginationRequest;
import top.aiolife.record.pojo.query.GithubCommitQuery;
import top.aiolife.record.pojo.entity.UserBindEntity;
import top.aiolife.record.pojo.vo.GithubCommitVO;
import top.aiolife.record.service.IGithubService;
import top.aiolife.record.service.IUserBindService;

import java.util.List;

/**
 * GitHub 相关接口（前端代理 GitHub API，规避浏览器 CORS）
 *
 * @author Lys
 * @date 2025/04/28 22:15
 */
@Slf4j
@RestController
@AllArgsConstructor
@RequestMapping("/github")
public class GitHubController {

    private final IGithubService githubService;

    private final IUserBindService userBindService;

    /**
     * 获取当前绑定 GitHub 账号的最近提交列表
     */
    @GetMapping("/recent-commits")
    public ApiResponse<List<GithubCommitVO>> queryRecentCommits(@QueryParams GithubCommitQuery query) {
        query.validatePagination();
        return recentCommits(query.getPage(), query.getPageSize());
    }

    public ApiResponse<List<GithubCommitVO>> recentCommits(Integer page, Integer perPage) {
        PaginationRequest.validate(page, perPage, 100);
        long userId = StpUtil.getLoginIdAsLong();
        UserBindEntity bind = userBindService.getBindByUserIdAndPlatform(userId, "github");
        if (bind == null || bind.getPlatformUsername() == null) {
            return ApiResponse.success(List.of());
        }
        List<GithubCommitVO> commits = githubService.searchRecentCommits(
                bind.getPlatformUsername(), bind.getAccessToken(), page, perPage);
        return ApiResponse.success(commits);
    }
}
