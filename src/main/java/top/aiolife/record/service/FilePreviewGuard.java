package top.aiolife.record.service;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.stereotype.Component;
import top.aiolife.record.pojo.entity.FileEntity;

import java.util.List;

/**
 * 文件预览/下载访问守卫。
 * 统一处理 /file/preview、/file/download 系列接口的登录态解析与归属校验，
 * 兼容 &lt;img&gt; 标签无法携带 Authorization 头、只能经 Cookie / URL 参数传递 token 的场景。
 *
 * @author Lys
 */
@Component
public class FilePreviewGuard {

    /**
     * 访问判定结果
     */
    public enum AccessDecision {
        /** 放行 */
        ALLOW,
        /** 未登录（401） */
        UNAUTHORIZED,
        /** 已登录但非属主/管理员（403） */
        FORBIDDEN
    }

    /**
     * 解析当前登录用户 ID。
     * 优先走 Sa-Token 上下文（Authorization 头）；未命中时回退到 Cookie / URL 参数中的 token。
     *
     * @return 登录用户 ID，未登录返回 null
     */
    public Long resolveLoginUserId() {
        if (StpUtil.isLogin()) {
            return StpUtil.getLoginIdAsLong();
        }
        String tokenName = StpUtil.getTokenName();
        String token = SaHolder.getRequest().getCookieValue(tokenName);
        if (token == null) {
            token = SaHolder.getRequest().getParam(tokenName);
        }
        if (token == null) {
            return null;
        }
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        Object loginId = StpUtil.getLoginIdByToken(token);
        return loginId == null ? null : Long.parseLong(loginId.toString());
    }

    /**
     * 判断用户是否为管理员
     *
     * @param userId 用户 ID
     * @return 是否管理员
     */
    public boolean isAdmin(Long userId) {
        if (userId == null) {
            return false;
        }
        List<String> roles = StpUtil.getRoleList(userId);
        return roles != null && roles.contains("admin");
    }

    /**
     * 校验对文件记录的访问权限：公开文件直接放行；私有文件要求登录，且仅属主或管理员可读。
     *
     * @param fileEntity 文件记录
     * @param userId     当前登录用户 ID（未登录为 null）
     * @return 访问判定结果
     */
    public AccessDecision check(FileEntity fileEntity, Long userId) {
        if (fileEntity.getIsPublic() == null || fileEntity.getIsPublic() != 0) {
            return AccessDecision.ALLOW;
        }
        if (userId == null) {
            return AccessDecision.UNAUTHORIZED;
        }
        if (isAdmin(userId)) {
            return AccessDecision.ALLOW;
        }
        if (fileEntity.getCreateUser() != null && !fileEntity.getCreateUser().equals(userId)) {
            return AccessDecision.FORBIDDEN;
        }
        return AccessDecision.ALLOW;
    }
}
