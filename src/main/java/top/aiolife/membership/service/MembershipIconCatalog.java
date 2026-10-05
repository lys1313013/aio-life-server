package top.aiolife.membership.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/** 只公开清单中的开发期内置图标，不接收路径、远程地址或上传文件。 */
@Component
public class MembershipIconCatalog {
    private final Map<String, Definition> icons = new LinkedHashMap<>();

    public MembershipIconCatalog(ObjectMapper mapper) throws IOException {
        try (var input = new ClassPathResource("static/membership-icons/membership-icons.json").getInputStream()) {
            List<Definition> definitions = mapper.readValue(input, new TypeReference<>() {});
            for (Definition definition : definitions) {
                if (definition.key() == null || !definition.key().matches("[a-z0-9_-]+")
                        || definition.file() == null || !definition.file().matches("[a-z0-9_-]+\\.(svg|png|webp|jpg|jpeg|ico)")
                        || (definition.darkFile() != null && !definition.darkFile().matches("[a-z0-9_-]+\\.(svg|png|webp|jpg|jpeg|ico)"))
                        || icons.putIfAbsent(definition.key(), definition) != null) {
                    throw new IllegalStateException("会员图标清单存在非法或重复条目");
                }
                if (!resource(definition.key()).exists()) throw new IllegalStateException("会员图标资源不存在: " + definition.key());
                if (!resource(definition.key(), true).exists()) throw new IllegalStateException("会员深色图标资源不存在: " + definition.key());
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Definition(String key, String name, String file, String darkFile, String source, String license) {}
    public record IconVO(String key, String name, String url) {}

    public List<IconVO> list() {
        return icons.values().stream().map(icon -> new IconVO(icon.key(), icon.name(),
                "/api/membership/provider-icons/" + icon.key())).toList();
    }

    public String normalizeKey(String key) {
        if (key == null || key.isBlank()) return null;
        require(key);
        return key;
    }

    public Definition require(String key) {
        Definition icon = icons.get(key);
        if (icon == null) throw new IllegalArgumentException("请选择有效的内置会员图标");
        return icon;
    }

    public Resource resource(String key) {
        return resource(key, false);
    }

    public String file(String key, boolean dark) {
        Definition icon = require(key);
        return dark && icon.darkFile() != null ? icon.darkFile() : icon.file();
    }

    public Resource resource(String key, boolean dark) {
        return new ClassPathResource("static/membership-icons/" + file(key, dark));
    }
}
