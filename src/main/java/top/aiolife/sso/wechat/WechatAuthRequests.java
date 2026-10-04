package top.aiolife.sso.wechat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 每个认证入口使用明确的凭证类型，禁止提交客户端声明的手机号或 openid。 */
public final class WechatAuthRequests {
    private WechatAuthRequests() {}

    public record Login(@NotBlank @Size(max = 256) String loginCode) {}
    public record Register(@NotBlank @Size(max = 128) String loginTicket) {}
    public record PhoneLogin(@NotBlank @Size(max = 128) String loginTicket,
                             @NotBlank @Size(max = 256) String phoneCode) {}
    public record Bind(@NotBlank @Size(max = 128) String loginTicket,
                       @NotBlank @Size(max = 200) String password) {}
    public record InitializePassword(@NotBlank @Size(max = 256) String loginCode,
                                     @NotBlank @Size(min = 8, max = 128) String newPassword) {}
}
