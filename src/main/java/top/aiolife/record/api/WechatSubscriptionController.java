package top.aiolife.record.api;

import io.swagger.v3.oas.annotations.Operation;

import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.record.notification.WechatSubscriptionService;
import top.aiolife.core.redis.RedisUtil;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/notification/channels/wechat-mini")
@RequiredArgsConstructor
public class WechatSubscriptionController {
    private final WechatSubscriptionService service;
    private final RedisUtil redis;

    @Operation(summary = "查询微信订阅通知配置")
    @GetMapping
    public ApiResponse<WechatSubscriptionService.Config> getConfig() {
        return ApiResponse.success(service.getConfig(StpUtil.getLoginIdAsLong()));
    }

    @Operation(summary = "发送微信订阅测试通知")
    @PostMapping("/test")
    public ApiResponse<Void> sendTest() {
        long userId = StpUtil.getLoginIdAsLong();
        if (!redis.setIfAbsent("notification:wechat-mini:test:" + userId, "1", 30, TimeUnit.SECONDS)) {
            throw new IllegalStateException("操作过于频繁，请 30 秒后重试");
        }
        service.sendTest(userId);
        return ApiResponse.success();
    }
}
