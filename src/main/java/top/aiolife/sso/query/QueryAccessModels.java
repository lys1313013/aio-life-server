package top.aiolife.sso.query;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

public final class QueryAccessModels {
    private QueryAccessModels() {}

    public record IssueRequest(@NotBlank @Pattern(regexp = "[a-zA-Z0-9_-]{1,64}") String appId) {}
    public record EvaluateRequest(@NotBlank @Size(max = 160) String token,
                                  @NotBlank @Pattern(regexp = "time_record") String dataset) {}
    public record Issued(String grantId, String accessToken, String tokenType, String audience,
                         String appId, Set<String> scopes, long expiresAt, int expiresIn) {}
    public record Decision(String userId, String appId, String grantId, String role,
                           Set<String> scopes, long expiresAt) {}
    // authorizerToken 只保存在受保护 Redis 中，不向客户端或日志序列化。
    record Grant(String tokenHash, String userId, String appId, String authorizerToken, long expiresAt) {}
}
