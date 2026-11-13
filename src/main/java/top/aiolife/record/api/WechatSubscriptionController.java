package top.aiolife.record.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.aiolife.core.id.IdCodec;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.web.CurrentUserId;
import top.aiolife.record.notification.WechatSubscriptionService;

/**
 * 微信订阅通知控制器。
 *
 * @author Lys
 */
@Tag(name = "微信订阅通知")
@RestController
@RequestMapping("/notification/channels/wechat-mini")
@RequiredArgsConstructor
public class WechatSubscriptionController {

    private final WechatSubscriptionService service;

    @Operation(summary = "查询微信订阅通知配置")
    @GetMapping
    public ApiResponse<WechatSubscriptionService.Config> getConfig(@CurrentUserId String userId) {
        return ApiResponse.success(service.getConfig(IdCodec.decode(userId)));
    }

    @Operation(summary = "发送微信订阅测试通知")
    @PostMapping("/test")
    public ApiResponse<Void> sendTest(@CurrentUserId String userId) {
        service.sendTest(IdCodec.decode(userId));
        return ApiResponse.success();
    }
}
