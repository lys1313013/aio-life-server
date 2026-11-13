package top.aiolife.sso.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import top.aiolife.core.id.IdCodec;
import top.aiolife.core.query.QueryParams;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.resq.PageResp;
import top.aiolife.core.web.CurrentUserId;
import top.aiolife.sso.convertor.SsoApiConvertor;
import top.aiolife.sso.pojo.entity.MessageEntity;
import top.aiolife.sso.pojo.query.MessagePageQuery;
import top.aiolife.sso.pojo.req.MessageCreateReq;
import top.aiolife.sso.pojo.vo.MessageVO;
import top.aiolife.sso.service.IMessageService;

/**
 * 用户消息控制器
 *
 * @author Lys
 * @date 2026/11/13
 */
@Tag(name = "消息")
@RestController
@RequestMapping("/message")
@RequiredArgsConstructor
@Validated
public class MessageController {

    private final IMessageService messageService;

    @Operation(summary = "查询我的消息")
    @GetMapping("/list")
    public ApiResponse<List<MessageVO>> list(@CurrentUserId String userId,
                                             @RequestParam(required = false) Boolean isRead) {
        List<MessageEntity> messages = messageService.listByUserId(IdCodec.decode(userId), isRead);
        return ApiResponse.success(SsoApiConvertor.INSTANCE.toMessageVOList(messages));
    }

    @Operation(summary = "分页查询我的消息")
    @GetMapping("/page")
    public ApiResponse<PageResp<MessageVO>> queryPage(@CurrentUserId String userId,
                                                      @QueryParams MessagePageQuery query) {
        return ApiResponse.success(SsoApiConvertor.INSTANCE.toMessageVOPage(
                messageService.pageForUser(IdCodec.decode(userId), query.getIsRead(),
                        query.getPage(), query.getPageSize())));
    }

    @Operation(summary = "查询未读消息数量")
    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Integer>> unreadCount(@CurrentUserId String userId) {
        long count = messageService.getUnreadCount(IdCodec.decode(userId));
        Map<String, Integer> result = new HashMap<>();
        result.put("count", Math.toIntExact(count));
        return ApiResponse.success(result);
    }

    @Operation(summary = "标记消息已读")
    @PutMapping("/read/{id}")
    public ApiResponse<Void> markAsRead(@CurrentUserId String userId, @PathVariable String id) {
        messageService.markAsRead(IdCodec.decode(id), IdCodec.decode(userId));
        return ApiResponse.success();
    }

    @Operation(summary = "标记全部消息已读")
    @PutMapping("/read-all")
    public ApiResponse<Void> markAllAsRead(@CurrentUserId String userId) {
        messageService.markAllAsRead(IdCodec.decode(userId));
        return ApiResponse.success();
    }

    @Operation(summary = "创建消息")
    @PostMapping
    public ApiResponse<MessageVO> create(@CurrentUserId String userId,
                                         @Valid @RequestBody MessageCreateReq messageReq) {
        MessageEntity message = SsoApiConvertor.INSTANCE.fromMessageCreateReq(messageReq);
        return ApiResponse.success(SsoApiConvertor.INSTANCE.toMessageVO(
                messageService.createForUser(IdCodec.decode(userId), message)));
    }

    @Operation(summary = "删除我的消息")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@CurrentUserId String userId, @PathVariable String id) {
        messageService.deleteMessage(IdCodec.decode(id), IdCodec.decode(userId));
        return ApiResponse.success();
    }
}
