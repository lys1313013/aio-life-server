package top.aiolife.capture.service;

import static top.aiolife.capture.service.CaptureValues.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Redis 提供24小时请求级重试保护，不保存采购历史，也不承诺永久或跨客户端商品去重。 先保留PENDING再进行SQL写入；明确回滚才清除；提交结果不明时保留PENDING并禁止自动重写。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CaptureRequests {
    public static final Duration RETENTION = Duration.ofHours(24);
    private static final DefaultRedisScript<Long> COMPLETE =
            new DefaultRedisScript<>(
                    "if redis.call('GET',KEYS[1]) == ARGV[1] then return"
                        + " redis.call('SET',KEYS[1],ARGV[2],'EX',ARGV[3]) and 1 else return 0 end",
                    Long.class);
    private static final DefaultRedisScript<Long> REMOVE =
            new DefaultRedisScript<>(
                    "if redis.call('GET',KEYS[1]) == ARGV[1] then return redis.call('DEL',KEYS[1])"
                        + " else return 0 end",
                    Long.class);
    private final StringRedisTemplate redis;
    private final ObjectMapper json;

    public static final class Attempt {
        private final String key;
        private final String pending;
        private final String hash;
        private final Instant expiresAt;
        private final JsonNode replay;
        private ObjectNode response;

        Attempt(String key, String pending, String hash, Instant expiresAt, JsonNode replay) {
            this.key = key;
            this.pending = pending;
            this.hash = hash;
            this.expiresAt = expiresAt;
            this.replay = replay;
        }
    }

    public Attempt begin(long user, String scope, JsonNode request) {
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("采集提交必须在事务中执行");
        String requestKey = text(request, "idempotencyKey", true, 100);
        String key =
                "aio:capture:request:v2:"
                        + user
                        + ":"
                        + scope
                        + ":"
                        + hash(json.getNodeFactory().textNode(requestKey));
        String hash = hash(request);
        Instant expiry = Instant.now().plus(RETENTION);
        ObjectNode pending =
                json.createObjectNode()
                        .put("state", "pending")
                        .put("hash", hash)
                        .put("token", UUID.randomUUID().toString())
                        .put("expiresAt", expiry.toString());
        String serialized = pending.toString();
        Boolean claimed;
        try {
            claimed = redis.opsForValue().setIfAbsent(key, serialized, RETENTION);
        } catch (RuntimeException e) {
            throw unavailable("重试保护服务不可用，本次未开始写入，请稍后使用原请求重试");
        }
        if (!Boolean.TRUE.equals(claimed)) {
            String cached;
            try {
                cached = redis.opsForValue().get(key);
            } catch (RuntimeException e) {
                throw unavailable("无法确认上次提交结果，请先检查设备、衣柜或书影记录，不要自动重新创建");
            }
            if (cached == null) throw conflict("无法确认上次提交结果，请先核对原业务记录，不要自动换键重试");
            JsonNode state = read(cached);
            if (!hash.equals(state.path("hash").asText())) throw conflict("同一幂等键不能提交不同内容");
            if (!"completed".equals(state.path("state").asText()))
                throw conflict("上次提交正在处理或结果待确认，请先检查原业务记录，不要自动重复创建");
            return new Attempt(
                    key,
                    null,
                    hash,
                    Instant.parse(state.path("expiresAt").asText()),
                    state.path("result"));
        }
        Attempt attempt = new Attempt(key, serialized, hash, expiry, null);
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        if (attempt.response == null) return;
                        ObjectNode completed =
                                json.createObjectNode()
                                        .put("state", "completed")
                                        .put("hash", attempt.hash)
                                        .put("expiresAt", attempt.expiresAt.toString());
                        completed.set("result", attempt.response.deepCopy());
                        try {
                            Long saved =
                                    redis.execute(
                                            COMPLETE,
                                            List.of(attempt.key),
                                            attempt.pending,
                                            completed.toString(),
                                            String.valueOf(RETENTION.toSeconds()));
                            if (!Long.valueOf(1).equals(saved)) markUnconfirmed(attempt);
                        } catch (RuntimeException e) {
                            markUnconfirmed(attempt);
                            log.warn("采集业务已提交，但短期重试结果缓存未确认；保留pending并要求核对记录");
                        }
                    }

                    @Override
                    public void afterCompletion(int status) {
                        if (status != STATUS_ROLLED_BACK) return;
                        try {
                            redis.execute(REMOVE, List.of(attempt.key), attempt.pending);
                        } catch (RuntimeException e) {
                            log.warn("采集事务已回滚，但pending清理失败；重试将要求用户先核对");
                        }
                    }
                });
        return attempt;
    }

    private void markUnconfirmed(Attempt attempt) {
        attempt.response.remove("replayExpiresAt");
        attempt.response.put("retryStatus", "unconfirmed");
        attempt.response.putArray("warnings").add("记录已保存，但重试结果缓存未确认；请以返回的物品ID核对，勿重新创建");
    }

    public JsonNode replay(Attempt attempt) {
        return attempt.replay;
    }

    public JsonNode finish(Attempt attempt, JsonNode response) {
        attempt.response = object(response, "response");
        attempt.response.put("replayExpiresAt", attempt.expiresAt.toString());
        return attempt.response;
    }

    private ResponseStatusException unavailable(String message) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, message);
    }

    public JsonNode read(String value) {
        try {
            return json.readTree(value);
        } catch (Exception e) {
            throw new IllegalStateException("短期请求结果读取失败", e);
        }
    }

    public String hash(JsonNode node) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(canonical(node).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String canonical(JsonNode n) {
        if (n.isObject()) {
            var names = new java.util.ArrayList<String>();
            n.fieldNames().forEachRemaining(names::add);
            names.sort(String::compareTo);
            return "{"
                    + names.stream()
                            .map(k -> json.valueToTree(k).toString() + ":" + canonical(n.get(k)))
                            .collect(java.util.stream.Collectors.joining(","))
                    + "}";
        }
        if (n.isArray()) {
            var parts = new java.util.ArrayList<String>();
            n.forEach(v -> parts.add(canonical(v)));
            return "[" + String.join(",", parts) + "]";
        }
        return n.toString();
    }
}
