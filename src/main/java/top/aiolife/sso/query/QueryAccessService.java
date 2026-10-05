package top.aiolife.sso.query;

import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import top.aiolife.sso.service.AccountStatusGuard;
import top.aiolife.sso.service.SecondaryLockGuard;
import top.aiolife.sso.service.IApiKeyService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QueryAccessService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String TOKEN_PATTERN = "aqt_[a-f0-9]{32}\\.[A-Za-z0-9_-]{43}";
    private final QueryAccessProperties properties;
    private final QueryGrantStore store;
    private final AccountStatusGuard accounts;
    private final SecondaryLockGuard locks;
    private final IApiKeyService apiKeys;

    /** 身份由常规 Sa-Token / API Key 拦截器验证，此处只做查询权限收敛。 */
    public QueryAccessModels.TokenDecision checkToken(String serviceKey, long userId, String token, boolean apiKey) {
        requireEnabled();
        if (!equal(properties.getServiceKey(), serviceKey) || !properties.getAllowedApps().contains("aio-life-query")) throw denied();
        checkUser(userId);
        long expiresAt;
        if (apiKey) {
            var key = apiKeys.getByApiKey(token);
            if (key == null || !Long.valueOf(userId).equals(key.getUserId())) throw denied();
            expiresAt = key.getExpiredAt() == null ? Long.MAX_VALUE
                    : key.getExpiredAt().atZone(ZoneId.systemDefault()).toEpochSecond();
        } else {
            requireSourceSession(Long.toString(userId), token);
            long timeout = StpUtil.getStpLogic().getTokenTimeout(token);
            if (timeout != -1 && timeout <= 0) throw denied();
            expiresAt = timeout == -1 ? Long.MAX_VALUE : Instant.now().getEpochSecond() + timeout;
        }
        if (expiresAt <= Instant.now().getEpochSecond()) throw denied();
        return new QueryAccessModels.TokenDecision(Long.toString(userId), "aio-life-query", hash(token).substring(0, 32),
                apiKey ? "api_key" : "login", "ai_time_reader", Set.of("time.read"), expiresAt);
    }

    public QueryAccessModels.Issued issue(long userId, String sessionToken, String appId) {
        requireEnabled();
        if (!properties.getAllowedApps().contains(appId)) throw denied();
        requireSourceSession(Long.toString(userId), sessionToken);
        checkUser(userId);
        store.rateLimit(userId);
        String id = UUID.randomUUID().toString().replace("-", "");
        byte[] secret = new byte[32]; RANDOM.nextBytes(secret);
        String token = "aqt_" + id + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        long expiresAt = Instant.now().plusSeconds(300).getEpochSecond();
        store.create(id, new QueryAccessModels.Grant(hash(token), Long.toString(userId), appId, sessionToken, expiresAt));
        return new QueryAccessModels.Issued(id, token, "Bearer", "aio-life-query", appId, Set.of("time.read"), expiresAt, 300);
    }

    public QueryAccessModels.Decision evaluate(String serviceKey, QueryAccessModels.EvaluateRequest request) {
        requireEnabled();
        if (!equal(properties.getServiceKey(), serviceKey)) throw denied();
        String token = request.token();
        if (token == null || !token.matches(TOKEN_PATTERN) || !"time_record".equals(request.dataset())) throw denied();
        String id = token.substring(4, 36);
        QueryAccessModels.Grant grant = store.read(id);
        if (grant == null || !equal(grant.tokenHash(), hash(token))
                || grant.expiresAt() <= Instant.now().getEpochSecond()
                || !properties.getAllowedApps().contains(grant.appId())) throw denied();
        requireSourceSession(grant.userId(), grant.authorizerToken());
        checkUser(Long.parseLong(grant.userId()));
        return new QueryAccessModels.Decision(grant.userId(), grant.appId(), id, "ai_time_reader",
                Set.of("time.read"), grant.expiresAt());
    }

    public void revoke(long userId, String grantId) {
        requireEnabled();
        var grant = store.read(grantId);
        if (grant == null || !Long.toString(userId).equals(grant.userId())) throw denied();
        store.delete(grantId);
    }

    private void checkUser(long userId) {
        if (!accounts.isActive(userId)) throw denied();
        locks.checkMenus(userId, "/mcp/tools", "/time/time-tracker", "/time/dashboard");
    }

    private void requireSourceSession(String userId, String token) {
        Object loginId = StpUtil.getLoginIdByToken(token);
        if (loginId == null || !userId.equals(loginId.toString())) throw denied();
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "查询授权未启用");
    }

    private static boolean equal(String a, String b) {
        return a != null && b != null && MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable", e); }
    }

    private static ResponseStatusException denied() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "查询授权不可用");
    }
}
