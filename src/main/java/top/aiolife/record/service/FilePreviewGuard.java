package top.aiolife.record.service;

import java.util.Set;
import top.aiolife.record.mapper.AttachmentBusinessMapper;

import lombok.RequiredArgsConstructor;
import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.stereotype.Component;
import top.aiolife.bankcard.mapper.BankCardFileMapper;
import top.aiolife.record.mapper.IBVideoMapper;
import top.aiolife.record.pojo.entity.FileEntity;
import top.aiolife.sso.service.AccountStatusGuard;
import top.aiolife.sso.service.SecondaryLockGuard;

import java.util.List;

/**
 * 文件预览/下载访问守卫。
 * 统一处理 /file/preview、/file/download 系列接口的登录态解析与归属校验，
 * 兼容 &lt;img&gt; 标签无法携带 Authorization 头、只能经 Cookie / URL 参数传递 token 的场景。
 *
 * @author Lys
 */
@Component
@RequiredArgsConstructor
public class FilePreviewGuard {

    private final SecondaryLockGuard secondaryLockGuard;
    private final BankCardFileMapper bankCardFileMapper;
    private final AccountStatusGuard accountStatusGuard;

    private final IBVideoMapper videoMapper;
    private final AttachmentBusinessMapper bindings;

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
            Long userId = StpUtil.getLoginIdAsLong();
            return accountStatusGuard.isActive(userId) ? userId : null;
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
        Long userId = loginId == null ? null : Long.parseLong(loginId.toString());
        return accountStatusGuard.isActive(userId) ? userId : null;
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
     * 私有入口要求登录并校验附件归属与业务授权；匿名公开读取使用独立内容地址。
     *
     * @param fileEntity 文件记录
     * @param userId     当前登录用户 ID（未登录为 null）
     * @return 访问判定结果
     */
    public AccessDecision check(FileEntity fileEntity, Long userId) {
        if ("b_video_cover".equals(fileEntity.getBizType())) {
            if (userId == null) return AccessDecision.UNAUTHORIZED;
            if (!userId.equals(fileEntity.getUserId()) || fileEntity.getBizId() == null)
                return AccessDecision.FORBIDDEN;
            secondaryLockGuard.checkMenus(userId, "/record/videoWatch");
            return videoMapper.countOwnedVideo(fileEntity.getBizId(), userId) > 0
                    ? AccessDecision.ALLOW : AccessDecision.FORBIDDEN;
        }
        if ("bank_card_template_face".equals(fileEntity.getBizType())) {
            if (userId==null) return AccessDecision.UNAUTHORIZED;
            if (isAdmin(userId)) return AccessDecision.ALLOW;
            if (fileEntity.getBizId()==null) return AccessDecision.FORBIDDEN;
            secondaryLockGuard.checkMenus(userId,"/finance/bank-cards");
            long visible = bankCardFileMapper.countVisibleTemplate(fileEntity.getBizId(), userId);
            return visible>0 ? AccessDecision.ALLOW : AccessDecision.FORBIDDEN;
        }
        if ("bank_card_face".equals(fileEntity.getBizType())) {
            if (userId == null) return AccessDecision.UNAUTHORIZED;
            if (!userId.equals(fileEntity.getUserId())) return AccessDecision.FORBIDDEN;
            secondaryLockGuard.checkMenus(userId, "/finance/bank-cards");
            if (fileEntity.getBizId() != null && bankCardFileMapper.countOwnedCard(fileEntity.getBizId(), userId) == 0) return AccessDecision.FORBIDDEN;
            return AccessDecision.ALLOW;
        }
        if (userId == null) {
            return AccessDecision.UNAUTHORIZED;
        }
        if (!userId.equals(fileEntity.getUserId()) && !(isAdmin(userId) && Set.of("feedback","feedback_comment").contains(fileEntity.getBizType()))) {
            return AccessDecision.FORBIDDEN;
        }
        String menu = switch (fileEntity.getBizType() == null ? "" : fileEntity.getBizType()) {
            case "wardrobe_item_cover" -> "/wardrobe";
            case "feedback", "feedback_comment" -> "/my-hub/feedback";
            case "movie_cover" -> "/record/movie";
            case "honor_record" -> "/my-hub/honor";
            case "read_cover" -> "/record/read";
            case "event_record" -> "/record/event-records";
            case "device_cover" -> "/my-hub/device";
            default -> null;
        };
        if (menu != null) secondaryLockGuard.checkMenus(userId, menu);
        if(fileEntity.getBizId()!=null && Set.of("movie_cover","read_cover","device_cover","wardrobe_item_cover","honor_record","event_record","feedback","feedback_comment","avatar").contains(fileEntity.getBizType())) {
            if(fileEntity.getUserId()==null || bindings.countOwned(fileEntity.getBizType(),fileEntity.getBizId(),fileEntity.getUserId())==0)
                return AccessDecision.FORBIDDEN;
        }
        return AccessDecision.ALLOW;
    }
}
