package top.aiolife.sso.service;

import java.security.SecureRandom;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import cn.hutool.crypto.SecureUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import top.aiolife.core.util.LogSafeException;
import top.aiolife.sso.config.AuthAbuseProperties;

/** Redis 中共享的密码登录额度和一次性邮件验证码。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthAttemptStore {
    private static final String LOGIN_KEY = "auth:{password-login}:";
    private static final String CODE_KEY = "auth:{email-code}:";
    public static final int CODE_MINUTES = 5;
    private static final int CODE_MAX_FAILURES = 5;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final RedisScript<Long> LOGIN_RESERVE = script("login-reserve");
    private static final RedisScript<Long> LOGIN_RELEASE = script("login-release");
    private static final RedisScript<Long> CODE_ISSUE = script("email-code-issue");
    private static final RedisScript<Long> CODE_CONSUME = script("email-code-consume");

    private final StringRedisTemplate redis;
    private final AuthAbuseProperties properties;

    public record LoginAttempt(String accountKey, String accountIpKey, String id) {}

    public enum EmailPurpose {
        REGISTER, RESET_PASSWORD, RESET_SECONDARY_PASSWORD
    }

    public LoginAttempt reserveLogin(String username, String ip) {
        var limits = properties.getLogin();
        String account = subjectKey(username);
        String source = SecureUtil.sha256(ip);
        var attempt = new LoginAttempt(LOGIN_KEY + "account:" + account,
                LOGIN_KEY + "account-ip:" + account + ":" + source, UUID.randomUUID().toString());
        long retryMillis = run(LOGIN_RESERVE,
                List.of(LOGIN_KEY + "ip:" + source, attempt.accountKey(), attempt.accountIpKey()),
                attempt.id(), Integer.toString(limits.getIpLimit()), Integer.toString(limits.getIpWindowSeconds()),
                Integer.toString(limits.getAccountLimit()), Integer.toString(limits.getAccountWindowSeconds()),
                Integer.toString(limits.getAccountIpLimit()), Integer.toString(limits.getAccountIpWindowSeconds()));
        if (retryMillis > 0) {
            throw rateLimited("登录尝试过于频繁，请稍后重试", retryMillis);
        }
        return attempt;
    }

    public void releaseSuccessfulLogin(LoginAttempt attempt) {
        run(LOGIN_RELEASE, List.of(attempt.accountKey(), attempt.accountIpKey()), attempt.id());
    }

    public String issueEmailCode(EmailPurpose purpose, String email, String ip) {
        var limits = properties.getCode();
        var now = ZonedDateTime.now(limits.getZone());
        var date = now.toLocalDate();
        long midnightMillis = date.plusDays(1).atStartOfDay(limits.getZone()).toInstant().toEpochMilli();
        String subject = subjectKey(email);
        String dayKey = CODE_KEY + "daily:" + date + ":";
        String code = String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
        long retryMillis = run(CODE_ISSUE,
                List.of(CODE_KEY + "cooldown:" + subject, dayKey + "ip:" + SecureUtil.sha256(ip),
                        dayKey + "email:" + subject, dayKey + "total", emailCodeKey(purpose, subject)),
                Integer.toString(limits.getIpLimitDay()), Integer.toString(limits.getEmailLimitDay()),
                Integer.toString(limits.getTotalLimitDay()), Long.toString(midnightMillis),
                Integer.toString(limits.getIntervalSeconds()), addressKey(email), code, Integer.toString(CODE_MINUTES * 60));
        if (retryMillis > 0) {
            throw rateLimited("验证码发送过于频繁，请稍后重试", retryMillis);
        }
        return code;
    }

    public void consumeEmailCode(EmailPurpose purpose, String email, String input) {
        String candidate = input != null && input.length() == 6 ? input : "";
        long result = run(CODE_CONSUME, List.of(emailCodeKey(purpose, subjectKey(email))),
                addressKey(email), candidate, Integer.toString(CODE_MAX_FAILURES));
        if (result == -1) {
            throw new IllegalArgumentException("验证码错误次数过多，已失效，请重新获取");
        }
        if (result != 1) {
            throw new IllegalArgumentException("验证码错误或已过期");
        }
    }

    private static String emailCodeKey(EmailPurpose purpose, String subject) {
        return CODE_KEY + "value:" + purpose.name() + ":" + subject;
    }

    private static String subjectKey(String subject) {
        // 逐字符折叠大小写，避免词尾西格玛等上下文规则为同一账号产生不同额度。
        int[] folded = Objects.toString(subject, "").codePoints()
                .map(Character::toUpperCase).map(Character::toLowerCase).toArray();
        return SecureUtil.sha256(new String(folded, 0, folded.length));
    }

    private static String addressKey(String email) {
        // 同一用途保留最新验证码，并绑定实际收件地址的大小写。
        return SecureUtil.sha256(Objects.toString(email, ""));
    }

    private long run(RedisScript<Long> script, List<String> keys, String... args) {
        try {
            Long result = redis.execute(script, keys, (Object[]) args);
            if (result != null) {
                return result;
            }
        } catch (DataAccessException e) {
            log.error("认证 Redis 操作失败", LogSafeException.withoutMessages(e));
        }
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "认证服务暂时不可用，请稍后重试");
    }

    private static RedisScript<Long> script(String name) {
        return RedisScript.of(new ClassPathResource("redis/auth/" + name + ".lua"), Long.class);
    }

    private static ResponseStatusException rateLimited(String message, long retryMillis) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, Long.toString(Math.ceilDiv(retryMillis, 1000)));
        return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, message) {
            @Override
            public HttpHeaders getHeaders() {
                return headers;
            }
        };
    }
}
