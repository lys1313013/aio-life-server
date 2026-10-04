package top.aiolife.sso.qr;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.crypto.SecureUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import top.aiolife.sso.mapper.UserMapper;
import top.aiolife.sso.pojo.vo.UserLoginVO;
import top.aiolife.sso.service.AccountStatusGuard;
import top.aiolife.sso.service.LoginSessionService;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;

import static top.aiolife.sso.qr.QrLoginModels.*;

@Service
@RequiredArgsConstructor
public class QrLoginService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final QrLoginStore store;
    private final AccountStatusGuard accounts;
    private final UserMapper users;
    private final LoginSessionService sessions;

    public Created create(String ip, String userAgent) {
        store.rateLimit("create", ip, 20);
        String id = random(), browser = random(), scan = random();
        String code = String.format(Locale.ROOT, "%04d", RANDOM.nextInt(10000));
        Instant now = Instant.now();
        store.create(id, new Ticket(hash(browser), hash(scan), "WAITING", browserLabel(userAgent),
                now.toString(), code, now.toEpochMilli() + 120_000, null, null, null));
        return new Created(id, browser, "aiolife://web-login?v=1&id=" + id + "&ticket=" + scan, code, 120, 2);
    }

    public Status status(String id, String secret) {
        var snapshot = browser(id, secret);
        if (snapshot == null) return new Status("EXPIRED", 0);
        return new Status(snapshot.ticket().status(), remaining(snapshot.ticket()));
    }

    public Scanned scan(ScanRequest request, long userId, String token) {
        store.rateLimit("scan", Long.toString(userId), 30);
        accounts.requireActive(userId);
        for (int attempt = 0; attempt < 3; attempt++) {
            var before = scanner(request.id(), request.ticket());
            var ticket = before.ticket();
            if (!ticket.status().equals("WAITING")) {
                requireOwner(ticket, userId, token);
                return scanned(ticket);
            }
            var next = ticket.transition("SCANNED", userId, token, null);
            if (store.replace(request.id(), before, next, 0)) return scanned(next);
        }
        throw conflict();
    }

    public Status decide(DecisionRequest request, long userId, String token) {
        store.rateLimit("decision", Long.toString(userId), 30);
        accounts.requireActive(userId);
        var before = scanner(request.id(), request.ticket());
        var ticket = before.ticket();
        requireOwner(ticket, userId, token);
        String nextState = request.approve() ? "CONFIRMED" : "REJECTED";
        if (ticket.status().equals(nextState)) return new Status(nextState, remaining(ticket));
        if (!ticket.status().equals("SCANNED")) throw conflict();
        if (!store.replace(request.id(), before, ticket.transition(nextState, userId, token, null), 0)) throw conflict();
        return new Status(nextState, remaining(ticket));
    }

    public UserLoginVO consume(BrowserRequest request, String ip) {
        var before = browser(request.id(), request.browserSecret());
        if (before == null) throw expired();
        var ticket = before.ticket();
        if (!Set.of("CONFIRMED", "CONSUMED").contains(ticket.status())) throw conflict();
        requireAuthorizer(ticket);
        if (ticket.status().equals("CONSUMED")) {
            if (StpUtil.getLoginIdByToken(ticket.result().getAccessToken()) == null) throw expired();
            return ticket.result();
        }
        // 先抢占且永不重新开放签发。进程崩溃时让票据自然过期，避免重复会话。
        var issuing = ticket.transition("ISSUING", ticket.userId(), ticket.authorizerToken(), null);
        if (!store.replace(request.id(), before, issuing, 0)) throw conflict();
        var claimed = store.read(request.id());
        UserLoginVO result = null;
        try {
            if (claimed == null || !claimed.ticket().status().equals("ISSUING")) throw expired();
            requireAuthorizer(ticket);
            var user = users.selectById(ticket.userId());
            if (user == null) throw expired();
            // 不向响应写 Cookie；凭证保存成功后仅在此浏览器的响应体中交付。
            result = sessions.completeQr(user, ip);
            var completed = ticket.transition("CONSUMED", ticket.userId(), ticket.authorizerToken(), result);
            if (!store.replace(request.id(), claimed, completed, 30)) throw expired();
            return result;
        } catch (RuntimeException e) {
            if (result != null) StpUtil.logoutByTokenValue(result.getAccessToken());
            if (claimed != null) store.replace(request.id(), claimed,
                    issuing.transition("FAILED", ticket.userId(), ticket.authorizerToken(), null), 0);
            throw e;
        }
    }

    public void cancel(BrowserRequest request) {
        var before = browser(request.id(), request.browserSecret());
        if (before == null) return;
        // 已兑换的登录不受路由卸载取消影响；兑换中的取消会阻止最终交付并撤销新会话。
        if (!before.ticket().status().equals("CONSUMED")) {
            if (!store.replace(request.id(), before, null, 0)) throw conflict();
        }
    }

    private QrLoginStore.Snapshot browser(String id, String secret) {
        var snapshot = store.read(id);
        if (snapshot == null) return null;
        requireSecret(secret, snapshot.ticket().browserHash());
        store.rateLimit("poll", id, 90);
        if (!snapshot.ticket().status().equals("CONSUMED") && remaining(snapshot.ticket()) == 0) return null;
        return snapshot;
    }

    private QrLoginStore.Snapshot scanner(String id, String secret) {
        var snapshot = store.read(id);
        if (snapshot == null || remaining(snapshot.ticket()) == 0) throw expired();
        requireSecret(secret, snapshot.ticket().scanHash());
        return snapshot;
    }

    private void requireOwner(Ticket ticket, long userId, String token) {
        if (!Long.valueOf(userId).equals(ticket.userId()) || !token.equals(ticket.authorizerToken())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "二维码已由其他会话扫描，请刷新二维码");
        }
    }

    private void requireAuthorizer(Ticket ticket) {
        Object loginId = StpUtil.getLoginIdByToken(ticket.authorizerToken());
        if (loginId == null || !loginId.toString().equals(ticket.userId().toString())) throw expired();
        if (StpUtil.getStpLogic().isOpenCheckActiveTimeout()) {
            try { StpUtil.getStpLogic().checkActiveTimeout(ticket.authorizerToken()); }
            catch (NotLoginException e) { throw expired(); }
        }
        accounts.requireActive(ticket.userId());
    }

    private void requireSecret(String supplied, String expected) {
        if (supplied == null || !supplied.matches(SECRET) || !MessageDigest.isEqual(
                hash(supplied).getBytes(StandardCharsets.US_ASCII), expected.getBytes(StandardCharsets.US_ASCII))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "扫码登录凭证无效");
        }
    }

    private Scanned scanned(Ticket ticket) {
        return new Scanned(ticket.status(), ticket.browser(), ticket.requestedAt(), ticket.verificationCode(), remaining(ticket));
    }
    private int remaining(Ticket ticket) { return (int) Math.max(0, (ticket.expiresAt() - System.currentTimeMillis() + 999) / 1000); }
    private static String random() { byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes); return HexFormat.of().formatHex(bytes); }
    private static String hash(String text) { return SecureUtil.sha256(text); }
    private ResponseStatusException expired() { return new ResponseStatusException(HttpStatus.BAD_REQUEST, "二维码已失效，请刷新后重新扫码"); }
    private ResponseStatusException conflict() { return new ResponseStatusException(HttpStatus.CONFLICT, "扫码登录状态已变化，请重试或刷新二维码"); }

    // 不回显原始 User-Agent，避免注入和暴露过多设备信息。
    static String browserLabel(String ua) {
        String value = ua == null ? "" : ua;
        String browser = value.contains("Edg/") ? "Edge" : value.contains("Firefox/") ? "Firefox"
                : value.contains("Chrome/") ? "Chrome" : value.contains("Safari/") ? "Safari" : "浏览器";
        String os = value.contains("Windows") ? "Windows" : value.contains("Android") ? "Android"
                : value.contains("iPhone") || value.contains("iPad") ? "iOS" : value.contains("Macintosh") ? "macOS"
                : value.contains("Linux") ? "Linux" : "未知系统";
        return browser + " · " + os;
    }
}
