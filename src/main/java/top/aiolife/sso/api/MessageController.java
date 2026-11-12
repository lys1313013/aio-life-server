package top.aiolife.sso.api;

import io.swagger.v3.oas.annotations.Operation;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.query.QueryParams;
import top.aiolife.core.query.PaginationRequest;
import top.aiolife.sso.pojo.query.MessagePageQuery;
import top.aiolife.sso.pojo.query.MessageAdminPageQuery;
import top.aiolife.core.resq.PageResp;
import top.aiolife.sso.convertor.SsoApiConvertor;
import top.aiolife.sso.pojo.entity.MessageEntity;
import top.aiolife.sso.pojo.req.MessageCreateReq;
import top.aiolife.sso.pojo.vo.MessageVO;
import top.aiolife.sso.service.IMessageService;

@RestController
@RequestMapping("/message")
@RequiredArgsConstructor
@Validated
public class MessageController {

    private final IMessageService messageService;

    @Operation(summary = "查询我的消息")
    @GetMapping("/list")
    public ApiResponse<List<MessageVO>> list(
            @RequestParam(required = false) Boolean isRead) {
        long userId = StpUtil.getLoginIdAsLong();
        List<MessageEntity> messages = messageService.listByUserId(userId, isRead);
        return ApiResponse.success(SsoApiConvertor.INSTANCE.toMessageVOList(messages));
    }

    @Operation(summary = "分页查询我的消息")
    @GetMapping("/page")
    public ApiResponse<PageResp<MessageVO>> queryPage(@QueryParams MessagePageQuery query) {
        query.validatePagination();
        return page(query.getIsRead(), query.getPage().longValue(), query.getPageSize().longValue());
    }

    public ApiResponse<PageResp<MessageVO>> page(Boolean isRead, Long current, Long size) {
        PaginationRequest.validate(current, size, 50);
        long userId = StpUtil.getLoginIdAsLong();
        return ApiResponse.success(SsoApiConvertor.INSTANCE.toMessageVOPage(
                messageService.pageByUserId(userId, isRead, current, size)));
    }

    @Operation(summary = "查询未读消息数量")
    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Integer>> unreadCount() {
        long userId = StpUtil.getLoginIdAsLong();
        long count = messageService.getUnreadCount(userId);
        Map<String, Integer> result = new HashMap<>();
        result.put("count", Math.toIntExact(count));
        return ApiResponse.success(result);
    }

    @Operation(summary = "标记消息已读")
    @PutMapping("/read/{id}")
    public ApiResponse<Void> markAsRead(@PathVariable Long id) {
        long userId = StpUtil.getLoginIdAsLong();
        messageService.markAsRead(id, userId);
        return ApiResponse.success();
    }

    @Operation(summary = "标记全部消息已读")
    @PutMapping("/read-all")
    public ApiResponse<Void> markAllAsRead() {
        long userId = StpUtil.getLoginIdAsLong();
        messageService.markAllAsRead(userId);
        return ApiResponse.success();
    }

    @Operation(summary = "创建消息")
    @PostMapping
    public ApiResponse<MessageVO> create(@Valid @RequestBody MessageCreateReq messageReq) {
        MessageEntity message = SsoApiConvertor.INSTANCE.fromMessageCreateReq(messageReq);
        long userId = StpUtil.getLoginIdAsLong();
        message.setCreateUser(userId);
        message.setUpdateUser(userId);
        message.setSenderId(userId);
        return ApiResponse.success(SsoApiConvertor.INSTANCE.toMessageVO(messageService.createMessage(message)));
    }

    @Operation(summary = "删除我的消息")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        long userId = StpUtil.getLoginIdAsLong();
        messageService.deleteMessage(id, userId);
        return ApiResponse.success();
    }

    @SaCheckRole("admin")
    @Operation(summary = "管理员分页查询消息")
    @GetMapping("/admin/list")
    public ApiResponse<PageResp<MessageVO>> queryAdminPage(@QueryParams MessageAdminPageQuery query) {
        query.validatePagination();
        return adminList(query.getPage().longValue(), query.getPageSize().longValue(), query.getUserId());
    }

    public ApiResponse<PageResp<MessageVO>> adminList(Long current, Long size, Long userId) {
        PaginationRequest.validate(current, size, PaginationRequest.MAX_PAGE_SIZE);
        return ApiResponse.success(SsoApiConvertor.INSTANCE.toMessageVOPage(messageService.adminList(current, size, userId)));
    }

    @SaCheckRole("admin")
    @Operation(summary = "管理员发送消息")
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
    @Operation(summary = "管理员删除消息")
    @DeleteMapping("/admin/{id}")
    public ApiResponse<Void> adminDelete(@PathVariable Long id) {
        messageService.adminDelete(id);
        return ApiResponse.success();
    }
}
