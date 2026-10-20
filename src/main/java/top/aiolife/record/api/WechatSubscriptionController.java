package top.aiolife.record.api;

import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.record.notification.WechatSubscriptionService;
import top.aiolife.record.util.RedisUtil;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/notification/channels/wechat-mini")
@RequiredArgsConstructor
public class WechatSubscriptionController {
    private final WechatSubscriptionService service;
    private final RedisUtil redis;

    @GetMapping
    public ApiResponse<WechatSubscriptionService.Config> getConfig() {
        return ApiResponse.success(service.getConfig(StpUtil.getLoginIdAsLong()));
    }

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
