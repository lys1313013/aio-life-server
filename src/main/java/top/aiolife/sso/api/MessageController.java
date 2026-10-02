package top.aiolife.sso.api;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.resq.PageResp;
import top.aiolife.sso.convertor.SsoApiConvertor;
import top.aiolife.sso.pojo.entity.MessageEntity;
import top.aiolife.sso.pojo.req.MessageCreateReq;
import top.aiolife.sso.pojo.vo.MessageVO;
import top.aiolife.sso.service.IMessageService;

@RestController
@RequestMapping("/message")
@RequiredArgsConstructor
public class MessageController {

    private final IMessageService messageService;

    @GetMapping("/list")
    public ApiResponse<List<MessageVO>> list(
            @RequestParam(required = false) Boolean isRead) {
        long userId = StpUtil.getLoginIdAsLong();
        List<MessageEntity> messages = messageService.listByUserId(userId, isRead);
        return ApiResponse.success(SsoApiConvertor.INSTANCE.toMessageVOList(messages));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Long>> unreadCount() {
        long userId = StpUtil.getLoginIdAsLong();
        long count = messageService.getUnreadCount(userId);
        Map<String, Long> result = new HashMap<>();
        result.put("count", count);
        return ApiResponse.success(result);
    }

    @PutMapping("/read/{id}")
    public ApiResponse<Void> markAsRead(@PathVariable Long id) {
        long userId = StpUtil.getLoginIdAsLong();
        messageService.markAsRead(id, userId);
        return ApiResponse.success();
    }

    @PutMapping("/read-all")
    public ApiResponse<Void> markAllAsRead() {
        long userId = StpUtil.getLoginIdAsLong();
        messageService.markAllAsRead(userId);
        return ApiResponse.success();
    }

    @PostMapping
    public ApiResponse<MessageVO> create(@Valid @RequestBody MessageCreateReq messageReq) {
        MessageEntity message = SsoApiConvertor.INSTANCE.fromMessageCreateReq(messageReq);
        long userId = StpUtil.getLoginIdAsLong();
        message.setCreateUser(userId);
        message.setUpdateUser(userId);
        message.setSenderId(userId);
        return ApiResponse.success(SsoApiConvertor.INSTANCE.toMessageVO(messageService.createMessage(message)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        long userId = StpUtil.getLoginIdAsLong();
        messageService.deleteMessage(id, userId);
        return ApiResponse.success();
    }

    @SaCheckRole("admin")
    @GetMapping("/admin/list")
    public ApiResponse<PageResp<MessageVO>> adminList(
            @RequestParam(required = false, defaultValue = "1") Long current,
            @RequestParam(required = false, defaultValue = "10") Long size,
            @RequestParam(required = false) Long userId
    ) {
        return ApiResponse.success(SsoApiConvertor.INSTANCE.toMessageVOPage(messageService.adminList(current, size, userId)));
    }

    @SaCheckRole("admin")
    @PostMapping("/admin/send")
    public ApiResponse<Void> adminSend(@Valid @RequestBody MessageCreateReq messageReq) {
        MessageEntity message = SsoApiConvertor.INSTANCE.fromMessageCreateReq(messageReq);
        message.setCreateUser(StpUtil.getLoginIdAsLong());
        message.setUpdateUser(StpUtil.getLoginIdAsLong());
        message.setSenderId(StpUtil.getLoginIdAsLong());
        messageService.createMessage(message);
        return ApiResponse.success();
    }

    @SaCheckRole("admin")
    @DeleteMapping("/admin/{id}")
    public ApiResponse<Void> adminDelete(@PathVariable Long id) {
        messageService.adminDelete(id);
        return ApiResponse.success();
    }
}
