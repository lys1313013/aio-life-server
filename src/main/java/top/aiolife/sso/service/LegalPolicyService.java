package top.aiolife.sso.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.Getter;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import top.aiolife.sso.pojo.entity.UserEntity;
import top.aiolife.system.service.ISystemConfigService;

/** 两端共用的协议正文与版本；仅公开运营者主动配置的联系信息。 */
@Service
public class LegalPolicyService {
    public static final String VERSION = "2026-10-27";
    private final ISystemConfigService config;
    @Getter private final Policies policies;

    public LegalPolicyService(ObjectMapper mapper, ISystemConfigService config) throws IOException {
        this.config = config;
        try (var input = new ClassPathResource("legal/policies.json").getInputStream()) {
            policies = mapper.readValue(input, Policies.class);
        }
        if (!VERSION.equals(policies.version())) throw new IllegalStateException("协议版本不一致");
    }

    public PublicPolicies current() {
        return new PublicPolicies(policies.version(), policies.effectiveDate(), policies.privacy(), policies.terms(),
                value("privacy.operator"), value("privacy.contact-email"));
    }

    private String value(String key) {
        String value = config.getValueByKey(key);
        return value == null ? "" : value.trim();
    }

    public static void requireAcceptance(Boolean agreed, String version) {
        if (!Boolean.TRUE.equals(agreed) || !VERSION.equals(version)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请阅读并同意当前版本的隐私政策和服务条款");
        }
    }

    public static void recordAcceptance(UserEntity user) {
        user.setPolicyVersion(VERSION);
        user.setPolicyAcceptedAt(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
    }

    public record Policies(String version, String effectiveDate, List<List<String>> privacy, List<List<String>> terms) {}
    public record PublicPolicies(String version, String effectiveDate, List<List<String>> privacy,
                                 List<List<String>> terms, String operator, String contactEmail) {}
}
