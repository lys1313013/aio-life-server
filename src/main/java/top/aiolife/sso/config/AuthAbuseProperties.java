package top.aiolife.sso.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.ZoneId;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Component
@Validated
@ConfigurationProperties(prefix = "aio.life.server.auth")
public class AuthAbuseProperties {
    @Valid
    private final Login login = new Login();
    @Valid
    private final Code code = new Code();

    @Getter
    @Setter
    public static class Login {
        @Positive private int ipLimit = 60;
        @Positive private int ipWindowSeconds = 60;
        @Positive private int accountIpLimit = 5;
        @Positive private int accountIpWindowSeconds = 600;
        @Positive private int accountLimit = 20;
        @Positive private int accountWindowSeconds = 900;
    }

    @Getter
    @Setter
    public static class Code {
        @Positive private int intervalSeconds = 180;
        @Positive private int ipLimitDay = 100;
        @Positive private int emailLimitDay = 20;
        @Positive private int totalLimitDay = 1000;
        @NotNull private ZoneId zone = ZoneId.of("Asia/Shanghai");
    }
}
