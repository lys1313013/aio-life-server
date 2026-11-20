package top.aiolife.sso.util;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.crypto.digest.DigestUtil;
import org.springframework.util.StringUtils;
import top.aiolife.core.redis.RedisUtil;

/** 菜单解锁只使用登录凭据的摘要标识会话。 */
public final class SecondaryLockSession {
    public static final String MCP_SESSION_HASH = SecondaryLockSession.class.getName() + ".mcpSessionHash";
    private static final String UNLOCK_KEY_PREFIX = "secondary:unlock:session:";

    private SecondaryLockSession() {}

    public static String currentHash() {
        if (StpUtil.isSwitch()) {
            // MCP 在线程切换前保存已认证会话的摘要，API Key 身份切换不携带此摘要。
            return (String) SaHolder.getStorage().get(MCP_SESSION_HASH);
        }
        return hash(StpUtil.getTokenValue());
    }

    public static String hash(String token) {
        return StringUtils.hasText(token) ? DigestUtil.sha256Hex(token) : null;
    }

    public static String unlockKey(long userId, String sessionHash, String menuPath) {
        return UNLOCK_KEY_PREFIX + userId + ":" + sessionHash + ":" + menuPath;
    }

    public static boolean isUnlocked(RedisUtil redis, long userId, String sessionHash, String menuPath) {
        return sessionHash != null && redis.hasKey(unlockKey(userId, sessionHash, menuPath));
    }
}
