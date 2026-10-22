package top.aiolife.capture.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;

/** 采集边界校验，禁止宽松数字转换掩盖错误。 */
public final class CaptureValues {
    private CaptureValues() {}

    public static String text(JsonNode n, String key, boolean required, int max) {
        JsonNode v = n.path(key);
        if (v.isMissingNode() || v.isNull()) {
            if (required) throw bad(key + "不能为空");
            return null;
        }
        if (!v.isTextual()) throw bad(key + "必须为字符串");
        String s = v.textValue().trim();
        if (s.length() > max || (required && s.isEmpty())) throw bad(key + "长度不正确");
        return s;
    }

    public static int integer(JsonNode n, String key, int min, int max) {
        JsonNode v = n.path(key);
        if (!v.isIntegralNumber()
                || !v.canConvertToInt()
                || v.intValue() < min
                || v.intValue() > max) throw bad(key + "范围不正确");
        return v.intValue();
    }

    public static Long id(JsonNode n, String key, boolean required) {
        String value = text(n, key, required, 20);
        if (value == null || value.isEmpty()) return null;
        try {
            long id = Long.parseLong(value);
            if (id <= 0) throw bad(key + "格式不正确");
            return id;
        } catch (NumberFormatException e) {
            throw bad(key + "格式不正确");
        }
    }

    public static BigDecimal money(JsonNode n, String key, boolean required) {
        String value = text(n, key, required, 20);
        if (value == null) return null;
        if (!value.matches("(?:0|[1-9][0-9]{0,7})(?:\\.[0-9]{1,2})?"))
            throw bad(key + "须为非负金额，最多两位小数");
        return new BigDecimal(value).setScale(2);
    }

    public static LocalDateTime date(JsonNode n, String key, boolean required) {
        String value = text(n, key, required, 19);
        if (value == null) return null;
        try {
            return LocalDateTime.parse(
                    value,
                    DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")
                            .withResolverStyle(java.time.format.ResolverStyle.STRICT));
        } catch (RuntimeException e) {
            throw bad(key + "日期格式不正确");
        }
    }

    public static String choice(JsonNode n, String key, Set<String> values) {
        String value = text(n, key, true, 32);
        if (!values.contains(value)) throw bad(key + "不支持");
        return value;
    }

    public static ObjectNode object(JsonNode n, String name) {
        if (!(n instanceof ObjectNode o)) throw bad(name + "必须为对象");
        return o;
    }

    public static void fields(JsonNode n, String... allowed) {
        Set<String> keys = Set.of(allowed);
        n.fieldNames()
                .forEachRemaining(
                        k -> {
                            if (!keys.contains(k)) throw bad("不支持的字段：" + k);
                        });
    }

    public static ResponseStatusException bad(String m) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, m);
    }

    public static ResponseStatusException conflict(String m) {
        return new ResponseStatusException(HttpStatus.CONFLICT, m);
    }

    public static ResponseStatusException missing() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "记录不存在或无权访问");
    }
}
