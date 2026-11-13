package top.aiolife.feedback.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import cn.dev33.satoken.annotation.SaCheckRole;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.aiolife.core.id.IdCodec;
import top.aiolife.core.query.CommonQuery;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.resq.PageResp;
import top.aiolife.core.web.CurrentUserId;
import top.aiolife.feedback.pojo.query.FeedbackAdminQuery;
import top.aiolife.feedback.pojo.req.FeedbackBatchReq;
import top.aiolife.feedback.pojo.req.FeedbackCommentCreateReq;
import top.aiolife.feedback.pojo.req.FeedbackStatusUpdateReq;
import top.aiolife.feedback.pojo.vo.AdminUserVO;
import top.aiolife.feedback.pojo.vo.FeedbackCommentVO;
import top.aiolife.feedback.pojo.vo.FeedbackDetailVO;
import top.aiolife.feedback.pojo.vo.FeedbackVO;
import top.aiolife.feedback.service.IFeedbackService;

/**
 * 反馈管理控制器（管理员）
 *
 * @author Ethan
 * @date 2026/07/19
 */
@Tag(name = "反馈管理")
@RestController
@RequestMapping("/feedback/admin")
@RequiredArgsConstructor
@SaCheckRole("admin")
public class FeedbackAdminController {

    private final IFeedbackService feedbackService;

    @Operation(summary = "管理员分页查询反馈")
    @GetMapping("/list")
    public ApiResponse<PageResp<FeedbackVO>> list(CommonQuery<FeedbackAdminQuery> query) {
        return ApiResponse.success(feedbackService.listAdmin(query));
    }

    @Operation(summary = "管理员查询反馈及评论")
    @GetMapping("/{id}")
    public ApiResponse<FeedbackDetailVO> detail(@PathVariable("id") String id) {
        return ApiResponse.success(feedbackService.getAdminDetail(IdCodec.decode(id)));
    }

    @Operation(summary = "管理员回复反馈")
    @PostMapping("/{id}/reply")
    public ApiResponse<FeedbackCommentVO> reply(@CurrentUserId String adminId, @PathVariable("id") String id,
                                                @Valid @RequestBody FeedbackCommentCreateReq req) {
        return ApiResponse.success(feedbackService.adminReply(
                IdCodec.decode(adminId), IdCodec.decode(id), req));
    }

    @Operation(summary = "管理员更新反馈状态")
    @PutMapping("/{id}/status")
    public ApiResponse<FeedbackVO> updateStatus(@CurrentUserId String adminId, @PathVariable("id") String id,
                                                @Valid @RequestBody FeedbackStatusUpdateReq req) {
        return ApiResponse.success(feedbackService.adminUpdateStatus(
                IdCodec.decode(adminId), IdCodec.decode(id), req));
    }

    @Operation(summary = "管理员批量处理反馈")
    @PostMapping("/batch")
    public ApiResponse<Void> batch(@CurrentUserId String adminId,
                                   @Valid @RequestBody FeedbackBatchReq req) {
        feedbackService.adminBatch(IdCodec.decode(adminId), req);
        return ApiResponse.success();
    }

    @Operation(summary = "查询反馈通知可选管理员")
    @GetMapping("/admin-users")
    public ApiResponse<List<AdminUserVO>> adminUsers() {
        return ApiResponse.success(feedbackService.listAdminUsers());
    }
}
