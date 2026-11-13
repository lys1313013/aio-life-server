package top.aiolife.sso.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import cn.dev33.satoken.annotation.SaCheckRole;
import jakarta.validation.Valid;
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
import top.aiolife.sso.pojo.query.MessageAdminPageQuery;
import top.aiolife.sso.pojo.req.MessageCreateReq;
import top.aiolife.sso.pojo.vo.MessageVO;
import top.aiolife.sso.service.IMessageService;

/**
 * 消息管理控制器
 *
 * @author Lys
 * @date 2026/11/13
 */
@Tag(name = "消息管理")
@RestController
@RequestMapping("/message/admin")
@RequiredArgsConstructor
@Validated
@SaCheckRole("admin")
public class MessageAdminController {

    private final IMessageService messageService;

    @Operation(summary = "管理员分页查询消息")
    @GetMapping("/list")
    public ApiResponse<PageResp<MessageVO>> queryPage(@QueryParams MessageAdminPageQuery query) {
        return ApiResponse.success(SsoApiConvertor.INSTANCE.toMessageVOPage(
                messageService.adminPage(query.getPage(), query.getPageSize(), query.getUserId())));
    }

    @Operation(summary = "管理员发送消息")
    @PostMapping("/send")
    public ApiResponse<Void> send(@CurrentUserId String adminId,
                                  @Valid @RequestBody MessageCreateReq messageReq) {
        MessageEntity message = SsoApiConvertor.INSTANCE.fromMessageCreateReq(messageReq);
        messageService.createByAdmin(IdCodec.decode(adminId), message);
        return ApiResponse.success();
    }

    @Operation(summary = "管理员删除消息")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> adminDelete(@PathVariable String id) {
        messageService.adminDelete(IdCodec.decode(id));
        return ApiResponse.success();
    }
}
