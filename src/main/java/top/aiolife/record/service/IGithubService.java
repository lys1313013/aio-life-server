package top.aiolife.record.service;

import java.util.List;
import top.aiolife.record.pojo.vo.GithubCommitVO;

/**
 * GitHub 数据服务接口
 *
 * @author Lys
 * @date 2026-01-02 23:23
 */
public interface IGithubService {

    /**
     * 获取指定用户最近的提交列表（服务端代理，规避浏览器 CORS）
     *
     * @param username GitHub 用户名
     * @param token    GitHub Access Token（可空）
     * @param page     页码，从 1 开始
     * @param perPage  每页数量
     * @return 提交列表
     */
    List<GithubCommitVO> searchRecentCommits(String username, String token, int page, int perPage);

    /**
     * 获取当前用户绑定 GitHub 账号的最近提交列表。
     *
     * @param userId  用户ID
     * @param page    页码，从 1 开始
     * @param perPage 每页数量
     * @return 提交列表；未绑定账号时返回空列表
     */
    List<GithubCommitVO> searchRecentCommitsForUser(long userId, int page, int perPage);
}
