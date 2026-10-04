package top.aiolife.sso.qr;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import top.aiolife.sso.pojo.vo.UserLoginVO;

/** 扫码票据和浏览器凭证是不同的随机值，不能相互代替。 */
public final class QrLoginModels {
    private QrLoginModels() {}
    public static final String SECRET = "[a-f0-9]{64}";

    public record BrowserRequest(@NotNull @Pattern(regexp = SECRET) String id,
                                 @NotNull @Pattern(regexp = SECRET) String browserSecret) {}
    public record ScanRequest(@NotNull @Pattern(regexp = SECRET) String id,
                              @NotNull @Pattern(regexp = SECRET) String ticket) {}
    public record DecisionRequest(@NotNull @Pattern(regexp = SECRET) String id,
                                  @NotNull @Pattern(regexp = SECRET) String ticket,
                                  @NotNull Boolean approve) {}
    public record Created(String id, String browserSecret, String qrContent, String verificationCode,
                          int expiresIn, int pollInterval) {}
    public record Status(String status, int expiresIn) {}
    public record Scanned(String status, String browser, String requestedAt, String verificationCode,
                          int expiresIn) {}

    // 仅在 Redis 内保存，绝不直接作为接口响应；token 不进入日志。
    public record Ticket(String browserHash, String scanHash, String status, String browser,
                         String requestedAt, String verificationCode, long expiresAt,
                         Long userId, String authorizerToken, UserLoginVO result) {
        public Ticket transition(String next, Long user, String token, UserLoginVO login) {
            return new Ticket(browserHash, scanHash, next, browser, requestedAt, verificationCode,
                    expiresAt, user, token, login);
        }
    }
}
