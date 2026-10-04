package top.aiolife.record.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Component
public class FeishuAppClient {

    private static final URI TOKEN_URI = URI.create(
            "https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal");
    private static final String SCOPE_URL =
            "https://open.feishu.cn/open-apis/contact/v3/scopes";
    private static final String USER_URL =
            "https://open.feishu.cn/open-apis/contact/v3/users/";
    private static final URI MESSAGE_URI = URI.create(
            "https://open.feishu.cn/open-apis/im/v1/messages?receive_id_type=open_id");
    private static final Pattern APP_ID_PATTERN = Pattern.compile("^cli_[A-Za-z0-9]+$");
    private static final Pattern OPEN_ID_PATTERN = Pattern.compile("^ou_[A-Za-z0-9]+$");

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Map<String, TokenCache> tokenCaches = new ConcurrentHashMap<>();

    public FeishuAppClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public void verifyCredentials(String appId, String appSecret) {
        validateCredentials(appId, appSecret);
        try {
            getTenantAccessToken(appId, appSecret, true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("飞书凭证验证被中断", e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("无法连接飞书，请稍后重试", e);
        }
    }

    public List<Recipient> listVisibleRecipients(String appId, String appSecret) {
        validateCredentials(appId, appSecret);
        try {
            String accessToken = getTenantAccessToken(appId, appSecret, false);
            List<String> openIds = listVisibleOpenIds(accessToken);
            List<Recipient> recipients = new ArrayList<>();
            for (String openId : openIds) {
                recipients.add(getRecipient(accessToken, openId));
            }
            return recipients;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("读取飞书接收用户被中断", e);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("读取飞书接收用户失败，请稍后重试", e);
        }
    }

    private List<String> listVisibleOpenIds(String accessToken) throws Exception {
        List<String> openIds = new ArrayList<>();
        String pageToken = null;
        do {
            UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(SCOPE_URL)
                    .queryParam("user_id_type", "open_id")
                    .queryParam("department_id_type", "open_department_id")
                    .queryParam("page_size", 100);
            if (StringUtils.hasText(pageToken)) {
                uriBuilder.queryParam("page_token", pageToken);
            }
            JsonNode body = parseSuccessfulBody(
                    getJson(uriBuilder.build().encode().toUri(), accessToken),
                    "读取飞书通讯录授权范围失败");
            JsonNode data = body.path("data");
            for (JsonNode item : data.path("user_ids")) {
                String openId = item.asText();
                if (StringUtils.hasText(openId) && !openIds.contains(openId)) {
                    validateOpenId(openId);
                    openIds.add(openId);
                }
            }
            pageToken = data.path("has_more").asBoolean(false)
                    ? data.path("page_token").asText()
                    : null;
            if (openIds.size() > 500) {
                throw new IllegalStateException("飞书可见用户超过 500 人，请缩小应用通讯录范围");
            }
        } while (StringUtils.hasText(pageToken));
        return openIds;
    }

    private Recipient getRecipient(String accessToken, String openId) {
        try {
            URI uri = UriComponentsBuilder.fromUriString(USER_URL + openId)
                    .queryParam("user_id_type", "open_id")
                    .build()
                    .encode()
                    .toUri();
            JsonNode body = parseSuccessfulBody(
                    getJson(uri, accessToken), "读取飞书用户信息失败");
            JsonNode user = body.path("data").path("user");
            String name = firstText(
                    user.path("name").asText(),
                    user.path("nickname").asText(),
                    user.path("en_name").asText());
            name = truncate(name, 128);
            return new Recipient(openId, StringUtils.hasText(name) ? name : null);
        } catch (Exception ignored) {
            // 用户 ID 已在授权范围内；缺少用户基础信息字段权限时仍可作为消息接收人。
            return new Recipient(openId, null);
        }
    }

    public SendResult send(
            String appId, String appSecret, String openId, NotificationRequest notification) {
        validateCredentials(appId, appSecret);
        validateOpenId(openId);
        try {
            SendResult result = sendWithToken(
                    openId, notification, getTenantAccessToken(appId, appSecret, false));
            if (result.tokenExpired()) {
                return sendWithToken(
                        openId, notification, getTenantAccessToken(appId, appSecret, true));
            }
            return result;
        } catch (HttpTimeoutException e) {
            return SendResult.failed("TIMEOUT", "请求飞书超时", true, false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return SendResult.failed("INTERRUPTED", "飞书发送被中断", true, false);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return SendResult.failed("CONFIG_ERROR", e.getMessage(), false, false);
        } catch (Exception e) {
            return SendResult.failed("NETWORK_ERROR", "飞书发送失败", true, false);
        }
    }

    public void validateAppId(String appId) {
        if (!StringUtils.hasText(appId) || !APP_ID_PATTERN.matcher(appId.trim()).matches()) {
            throw new IllegalArgumentException("飞书 App ID 格式无效");
        }
    }

    public void validateOpenId(String openId) {
        if (!StringUtils.hasText(openId) || !OPEN_ID_PATTERN.matcher(openId.trim()).matches()) {
            throw new IllegalArgumentException("飞书 open_id 格式无效");
        }
    }

    private void validateCredentials(String appId, String appSecret) {
        validateAppId(appId);
        if (!StringUtils.hasText(appSecret)) {
            throw new IllegalArgumentException("飞书 App Secret 不能为空");
        }
    }

    private SendResult sendWithToken(String openId, NotificationRequest notification, String accessToken)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("receive_id", openId);
        body.put("msg_type", "post");
        body.put("content", objectMapper.writeValueAsString(buildPostContent(notification)));

        HttpRequest request = HttpRequest.newBuilder(MESSAGE_URI)
                .timeout(Duration.ofSeconds(5))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return parseSendResponse(response);
    }

    private Map<String, Object> buildPostContent(NotificationRequest notification) {
        List<List<Map<String, String>>> lines = new ArrayList<>();
        lines.add(List.of(Map.of("tag", "text", "text", truncate(notification.textContent(), 4000))));
        if (StringUtils.hasText(notification.actionUrl())) {
            lines.add(List.of(Map.of(
                    "tag", "a",
                    "text", "查看详情",
                    "href", notification.actionUrl())));
        }
        return Map.of("zh_cn", Map.of(
                "title", truncate(notification.title(), 100),
                "content", lines));
    }

    private SendResult parseSendResponse(HttpResponse<String> response) throws Exception {
        if (response.statusCode() == 429 || response.statusCode() >= 500) {
            return SendResult.failed(String.valueOf(response.statusCode()), "飞书服务暂时不可用", true, false);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return SendResult.failed(String.valueOf(response.statusCode()), "飞书消息请求失败", false, false);
        }
        JsonNode body = objectMapper.readTree(response.body());
        int code = body.path("code").asInt(-1);
        if (code == 0) {
            return SendResult.succeeded();
        }
        String message = code == 99992361
                ? "绑定用户不属于当前飞书应用，请重新授权绑定"
                : sanitizeMessage(body.path("msg").asText());
        boolean tokenExpired = code == 99991663 || code == 99991664;
        boolean retryable = code == 9499;
        return SendResult.failed(String.valueOf(code), message, retryable, tokenExpired);
    }

    private String getTenantAccessToken(String appId, String appSecret, boolean forceRefresh) throws Exception {
        validateCredentials(appId, appSecret);
        String cacheKey = appId.trim();
        TokenCache current = tokenCaches.get(cacheKey);
        if (!forceRefresh && isUsable(current)) {
            return current.token();
        }
        synchronized (tokenCaches) {
            current = tokenCaches.get(cacheKey);
            if (!forceRefresh && isUsable(current)) {
                return current.token();
            }
            HttpResponse<String> response = sendJson(
                    TOKEN_URI,
                    Map.of("app_id", appId.trim(), "app_secret", appSecret),
                    null);
            JsonNode body = parseSuccessfulBody(response, "获取飞书 tenant_access_token 失败");
            String token = body.path("tenant_access_token").asText();
            if (!StringUtils.hasText(token)) {
                throw new IllegalStateException("飞书应用凭证无效或应用不可用");
            }
            long expiresIn = Math.max(body.path("expire").asLong(7200), 120);
            tokenCaches.put(cacheKey, new TokenCache(token, Instant.now().plusSeconds(expiresIn)));
            return token;
        }
    }

    private HttpResponse<String> sendJson(URI uri, Object payload, String bearerToken) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json; charset=utf-8");
        if (StringUtils.hasText(bearerToken)) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }
        HttpRequest request = builder
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> getJson(URI uri, String bearerToken) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(5))
                .header("Authorization", "Bearer " + bearerToken)
                .header("Content-Type", "application/json; charset=utf-8")
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode parseSuccessfulBody(HttpResponse<String> response, String fallbackMessage) throws Exception {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException(fallbackMessage);
        }
        JsonNode body = objectMapper.readTree(response.body());
        if (body.has("code") && body.path("code").asInt(-1) != 0) {
            String message = sanitizeMessage(body.path("msg").asText());
            throw new IllegalStateException(StringUtils.hasText(message) ? message : fallbackMessage);
        }
        return body;
    }

    private boolean isUsable(TokenCache cache) {
        return cache != null && cache.expiresAt().isAfter(Instant.now().plusSeconds(60));
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength - 1) + "…";
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String sanitizeMessage(String message) {
        if (!StringUtils.hasText(message)) {
            return "飞书返回未知错误";
        }
        return truncate(message.replaceAll("(?i)(token|secret)[^\\s,}]*", "$1=[redacted]"), 200);
    }

    private record TokenCache(String token, Instant expiresAt) {
    }

    public record Recipient(String openId, String name) {
    }

    public record SendResult(
            boolean success,
            String providerCode,
            String errorMessage,
            boolean retryable,
            boolean tokenExpired) {
        static SendResult succeeded() {
            return new SendResult(true, "0", null, false, false);
        }

        static SendResult failed(
                String providerCode, String errorMessage, boolean retryable, boolean tokenExpired) {
            return new SendResult(false, providerCode, errorMessage, retryable, tokenExpired);
        }
    }
}
