package top.aiolife.record.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.aiolife.core.id.IdCodec;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.web.CurrentUserId;
import top.aiolife.record.pojo.req.FeishuChannelSaveReq;
import top.aiolife.record.pojo.req.NotificationPreferenceUpdateReq;
import top.aiolife.record.pojo.vo.FeishuChannelConfigVO;
import top.aiolife.record.pojo.vo.FeishuRecipientListVO;
import top.aiolife.record.pojo.vo.NotificationPreferenceVO;
import top.aiolife.record.service.FeishuNotificationService;

import java.util.List;

/**
 * 通知渠道控制器。
 *
 * @author Lys
 */
@Tag(name = "通知渠道")
@RestController
@RequestMapping("/notification")
@RequiredArgsConstructor
public class NotificationChannelController {

    private final FeishuNotificationService notificationService;

    @Operation(summary = "查询飞书通知配置")
    @GetMapping("/channels/feishu")
    public ApiResponse<FeishuChannelConfigVO> getFeishuConfig(@CurrentUserId String userId) {
        return ApiResponse.success(notificationService.getConfig(IdCodec.decode(userId)));
    }

    @Operation(summary = "保存飞书通知配置")
    @PutMapping("/channels/feishu")
    public ApiResponse<FeishuChannelConfigVO> saveFeishuConfig(@CurrentUserId String userId,
                                                               @Valid @RequestBody FeishuChannelSaveReq req) {
        return ApiResponse.success(notificationService.saveConfig(IdCodec.decode(userId), req));
    }

    @Operation(summary = "查询飞书通知接收人")
    @GetMapping("/channels/feishu/recipients")
    public ApiResponse<FeishuRecipientListVO> listFeishuRecipients(@CurrentUserId String userId) {
        return ApiResponse.success(notificationService.listRecipients(IdCodec.decode(userId)));
    }

    @Operation(summary = "删除飞书通知配置")
    @DeleteMapping("/channels/feishu")
    public ApiResponse<Void> deleteFeishuConfig(@CurrentUserId String userId) {
        notificationService.deleteConfig(IdCodec.decode(userId));
        return ApiResponse.success();
    }

    @Operation(summary = "发送飞书测试通知")
    @PostMapping("/channels/feishu/test")
    public ApiResponse<Void> testFeishuConfig(@CurrentUserId String userId) {
        notificationService.sendTest(IdCodec.decode(userId));
        return ApiResponse.success();
    }

    @Operation(summary = "查询业务通知偏好")
    @GetMapping("/preferences")
    public ApiResponse<List<NotificationPreferenceVO>> listPreferences(@CurrentUserId String userId) {
        return ApiResponse.success(notificationService.listPreferences(IdCodec.decode(userId)));
    }

    @Operation(summary = "更新业务通知偏好")
    @PutMapping("/preferences")
    public ApiResponse<List<NotificationPreferenceVO>> updatePreferences(
            @CurrentUserId String userId,
            @Valid @RequestBody NotificationPreferenceUpdateReq req) {
        return ApiResponse.success(notificationService.updatePreferences(IdCodec.decode(userId), req));
    }
}
