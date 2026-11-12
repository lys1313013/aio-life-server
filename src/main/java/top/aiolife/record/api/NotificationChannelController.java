package top.aiolife.record.api;

import io.swagger.v3.oas.annotations.Operation;

import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.record.pojo.req.FeishuChannelSaveReq;
import top.aiolife.record.pojo.req.NotificationPreferenceUpdateReq;
import top.aiolife.record.pojo.vo.FeishuChannelConfigVO;
import top.aiolife.record.pojo.vo.FeishuRecipientListVO;
import top.aiolife.record.pojo.vo.NotificationPreferenceVO;
import top.aiolife.record.service.FeishuNotificationService;
import top.aiolife.core.redis.RedisUtil;

import java.util.List;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/notification")
@RequiredArgsConstructor
public class NotificationChannelController {

    private final FeishuNotificationService notificationService;
    private final RedisUtil redisUtil;

    @Operation(summary = "查询飞书通知配置")
    @GetMapping("/channels/feishu")
    public ApiResponse<FeishuChannelConfigVO> getFeishuConfig() {
        return ApiResponse.success(notificationService.getConfig(StpUtil.getLoginIdAsLong()));
    }

    @Operation(summary = "保存飞书通知配置")
    @PutMapping("/channels/feishu")
    public ApiResponse<FeishuChannelConfigVO> saveFeishuConfig(@RequestBody FeishuChannelSaveReq req) {
        return ApiResponse.success(notificationService.saveConfig(StpUtil.getLoginIdAsLong(), req));
    }

    @Operation(summary = "查询飞书通知接收人")
    @GetMapping("/channels/feishu/recipients")
    public ApiResponse<FeishuRecipientListVO> listFeishuRecipients() {
        return ApiResponse.success(notificationService.listRecipients(StpUtil.getLoginIdAsLong()));
    }

    @Operation(summary = "删除飞书通知配置")
    @DeleteMapping("/channels/feishu")
    public ApiResponse<Void> deleteFeishuConfig() {
        notificationService.deleteConfig(StpUtil.getLoginIdAsLong());
        return ApiResponse.success();
    }

    @Operation(summary = "发送飞书测试通知")
    @PostMapping("/channels/feishu/test")
    public ApiResponse<Void> testFeishuConfig() {
        long userId = StpUtil.getLoginIdAsLong();
        if (!redisUtil.setIfAbsent("notification:feishu:test:" + userId, "1", 30, TimeUnit.SECONDS)) {
            throw new IllegalStateException("操作过于频繁，请 30 秒后重试");
        }
        notificationService.sendTest(userId);
        return ApiResponse.success();
    }

    @Operation(summary = "查询业务通知偏好")
    @GetMapping("/preferences")
    public ApiResponse<List<NotificationPreferenceVO>> listPreferences() {
        return ApiResponse.success(notificationService.listPreferences(StpUtil.getLoginIdAsLong()));
    }

    @Operation(summary = "更新业务通知偏好")
    @PutMapping("/preferences")
    public ApiResponse<List<NotificationPreferenceVO>> updatePreferences(
            @RequestBody NotificationPreferenceUpdateReq req) {
        return ApiResponse.success(notificationService.updatePreferences(StpUtil.getLoginIdAsLong(), req));
    }
}
