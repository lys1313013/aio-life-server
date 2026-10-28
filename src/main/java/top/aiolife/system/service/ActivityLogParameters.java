package top.aiolife.system.service;

import cn.hutool.core.net.NetUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.servlet.HandlerMapping;

/** 只读取安全字段的绑定值，不序列化整个 DTO、不再次读取请求流。 */
@Component
@RequiredArgsConstructor
public class ActivityLogParameters {
    private static final List<String> SENSITIVE = List.of("password", "passwd", "pwd", "token", "cookie", "authorization",
            "secret", "salt", "apikey", "accesskey", "privatekey", "publickey", "credential", "signature", "verificationcode",
            "verifycode", "authcode", "otp", "captcha", "smscode", "emailcode", "securitycode", "cvv", "cvc");
    private static final List<String> ROUTES = List.of("/tasks", "/taskDetails", "/goals", "/timeRecord", "/anniversaryRecords", "/life-footprints");
    private static final List<String> QUERY_FIELDS = List.of("id", "taskId", "columnId", "page", "pageSize", "size", "get", "type", "status", "isPinned", "startDate", "endDate");
    private static final List<String> BODY_FIELDS = List.of("id", "taskId", "columnId", "status", "isCompleted", "priority", "sortOrder", "type", "version");
    private final ObjectMapper objectMapper;
    private String instanceIp;

    @PostConstruct
    void initializeIp() {
        try { instanceIp = NetUtil.getLocalhostStr(); }
        catch (Exception ignored) { instanceIp = null; }
    }

    public String serverIp() { return instanceIp; }

    public String capture(HttpServletRequest request, ProceedingJoinPoint point, String route) {
        if (route == null || denied(route) || request.getContentLengthLong() > 65536) return null;
        if (ROUTES.stream().noneMatch(prefix -> route.equals(prefix) || route.startsWith(prefix + "/"))) return null;
        try {
            var budget = new Budget();
            Map<String, Object> result = new LinkedHashMap<>();
            Map<String, Object> query = new LinkedHashMap<>();
            Map<String, String[]> parameters = request.getParameterMap();
            boolean form = request.getContentType() != null
                    && request.getContentType().toLowerCase(Locale.ROOT).startsWith("application/x-www-form-urlencoded");
            Map<String, String[]> queryParameters = form ? queryOnly(request.getQueryString()) : parameters;
            for (String field : QUERY_FIELDS) {
                if (queryParameters.containsKey(field) && !sensitive(field)) query.put(field, clean(queryParameters.get(field), 0, budget));
            }
            if (form) {
                var formBody = new LinkedHashMap<String, Object>();
                for (String field : BODY_FIELDS) {
                    if (sensitive(field) || !parameters.containsKey(field)) continue;
                    var values = new ArrayList<>(List.of(parameters.get(field)));
                    for (String queryValue : queryParameters.getOrDefault(field, new String[0])) values.remove(queryValue);
                    if (!values.isEmpty()) formBody.put(field, clean(values, 0, budget));
                }
                if (!formBody.isEmpty()) result.put("body", formBody);
            }
            if (!query.isEmpty()) result.put("query", query);
            Object variables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
            if (variables instanceof Map<?, ?> map) {
                var path = new LinkedHashMap<String, Object>();
                for (String field : List.of("id")) {
                    if (map.containsKey(field) && !sensitive(field)) path.put(field, clean(map.get(field), 0, budget));
                }
                if (!path.isEmpty()) result.put("path", path);
            }
            boolean hasBody = false;
            if (point.getSignature() instanceof MethodSignature signature) {
                var methodParameters = signature.getMethod().getParameters();
                Object[] args = point.getArgs();
                for (int i = 0; i < methodParameters.length; i++) {
                    if (methodParameters[i].isAnnotationPresent(RequestBody.class)) {
                        hasBody = args[i] != null;
                        Object body = readBody(args[i], BODY_FIELDS, budget, 0);
                        if (body != null) result.put("body", body);
                    }
                }
            }
            if (result.isEmpty() && (hasBody || !parameters.isEmpty()
                    || variables instanceof Map<?, ?> map && !map.isEmpty()
                    || request.getContentLengthLong() > 0)) return null;
            String json = objectMapper.writeValueAsString(result);
            return json.getBytes(StandardCharsets.UTF_8).length <= 32768 ? json : null;
        } catch (Exception ignored) {
            // 不打印异常详情，其中可能包含待脱敏的原始参数。
            return null;
        }
    }

    private Map<String, String[]> queryOnly(String query) {
        var values = new LinkedHashMap<String, List<String>>();
        if (query == null) return Map.of();
        if (query.length() > 65536) throw new IllegalArgumentException();
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = parts.length == 2 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
            values.computeIfAbsent(key, ignored -> new ArrayList<>()).add(value);
            if (values.size() > 1000) throw new IllegalArgumentException();
        }
        var result = new LinkedHashMap<String, String[]>();
        values.forEach((key, items) -> result.put(key, items.toArray(String[]::new)));
        return result;
    }

    private Object readBody(Object body, List<String> fields, Budget budget, int depth) {
        if (body == null) return null;
        if (depth > 8) throw new IllegalArgumentException();
        budget.visit();
        if (body instanceof Iterable<?> values) {
            var rows = new ArrayList<>();
            for (Object value : values) {
                Object row = readBody(value, fields, budget, depth + 1);
                if (row != null) rows.add(row);
            }
            return rows.isEmpty() ? null : rows;
        }
        var result = new LinkedHashMap<String, Object>();
        var bean = new BeanWrapperImpl(body);
        for (String field : fields) {
            // 只支持明确的单字段白名单，不执行任意属性路径。
            if (!field.matches("[a-zA-Z][a-zA-Z0-9_]*") || sensitive(field)) continue;
            if (body instanceof Map<?, ?> map) {
                if (map.containsKey(field)) result.put(field, clean(map.get(field), 0, budget));
            } else if (bean.isReadableProperty(field)) result.put(field, clean(bean.getPropertyValue(field), 0, budget));
        }
        return result.isEmpty() ? null : result;
    }

    private Object clean(Object value, int depth, Budget budget) {
        budget.visit();
        if (depth > 8) throw new IllegalArgumentException();
        if (value == null || value instanceof Boolean || value instanceof Integer || value instanceof Short
                || value instanceof Byte || value instanceof BigDecimal) return value;
        if (value instanceof Double number) {
            if (!Double.isFinite(number)) throw new IllegalArgumentException();
            return number;
        }
        if (value instanceof Float number) {
            if (!Float.isFinite(number)) throw new IllegalArgumentException();
            return number;
        }
        if (value instanceof Long || value instanceof BigInteger) return value.toString();
        if (value instanceof String text) {
            if (text.length() > 2000) throw new IllegalArgumentException();
            if (text.contains("://") && (text.contains("?") || text.contains("@"))) return "[REDACTED]";
            return text;
        }
        if (value instanceof Enum<?> item) return item.name();
        if (value instanceof Map<?, ?> map) {
            var result = new LinkedHashMap<String, Object>();
            for (var entry : map.entrySet()) {
                budget.visit();
                String key = entry.getKey().toString();
                if (key.length() > 2000) throw new IllegalArgumentException();
                result.put(key, sensitive(key) ? "[REDACTED]" : clean(entry.getValue(), depth + 1, budget));
            }
            return result;
        }
        var result = new ArrayList<>();
        if (value instanceof Iterable<?> values) {
            for (Object item : values) result.add(clean(item, depth + 1, budget));
            return result;
        }
        if (value.getClass().isArray()) {
            for (int i = 0; i < Array.getLength(value); i++) result.add(clean(Array.get(value, i), depth + 1, budget));
            return result;
        }
        // 未明确支持的对象不调用 toString 或完整序列化。
        return null;
    }

    private static boolean sensitive(String key) {
        String normalized = key.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        return SENSITIVE.stream().anyMatch(normalized::contains) || normalized.equals("code") || normalized.equals("key");
    }

    private static boolean denied(String route) {
        return List.of("/auth", "/password", "/bank-cards", "/bankCard", "/api-key", "/system-config", "/system/config", "/userbinds", "/body-profile", "/system/logs", "/mcp")
                .stream().anyMatch(prefix -> route.equals(prefix) || route.startsWith(prefix + "/"));
    }

    private static class Budget {
        private int nodes;
        void visit() { if (++nodes > 1000) throw new IllegalArgumentException(); }
    }
}
