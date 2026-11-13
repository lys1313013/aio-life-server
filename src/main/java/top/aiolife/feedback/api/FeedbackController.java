package top.aiolife.feedback.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.aiolife.core.id.IdCodec;
import top.aiolife.core.query.CommonQuery;
import top.aiolife.core.query.QueryParams;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.resq.PageResp;
import top.aiolife.core.web.CurrentUserId;
import top.aiolife.feedback.pojo.query.FeedbackAdminQuery;
import top.aiolife.feedback.pojo.req.FeedbackCommentCreateReq;
import top.aiolife.feedback.pojo.req.FeedbackCreateReq;
import top.aiolife.feedback.pojo.vo.FeedbackCommentVO;
import top.aiolife.feedback.pojo.vo.FeedbackDetailVO;
import top.aiolife.feedback.pojo.vo.FeedbackVO;
import top.aiolife.feedback.service.IFeedbackService;

/**
 * 用户反馈控制器（用户侧）
 *
 * @author Ethan
 * @date 2026/07/19
 */
@Tag(name = "反馈")
@RestController
@RequestMapping("/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final IFeedbackService feedbackService;

    @Operation(summary = "提交反馈")
    @PostMapping
    public ApiResponse<FeedbackVO> create(@CurrentUserId String userId,
                                          @Valid @RequestBody FeedbackCreateReq req) {
        return ApiResponse.success(feedbackService.create(IdCodec.decode(userId), req));
    }

    @Operation(summary = "查询我的反馈")
    @GetMapping("/my")
    public ApiResponse<PageResp<FeedbackVO>> listMy(@CurrentUserId String userId,
                                                    @QueryParams CommonQuery<FeedbackAdminQuery> query) {
        return ApiResponse.success(feedbackService.listMy(IdCodec.decode(userId), query));
    }

    @Operation(summary = "查询我的反馈详情")
    @GetMapping("/my/{id}")
    public ApiResponse<FeedbackDetailVO> getMyDetail(@CurrentUserId String userId, @PathVariable("id") String id) {
        return ApiResponse.success(feedbackService.getMyDetail(IdCodec.decode(userId), IdCodec.decode(id)));
    }

    @Operation(summary = "追加反馈评论")
    @PostMapping("/{id}/comment")
    public ApiResponse<FeedbackCommentVO> addComment(@CurrentUserId String userId,
                                                     @PathVariable("id") String id,
                                                     @Valid @RequestBody FeedbackCommentCreateReq req) {
        return ApiResponse.success(feedbackService.addComment(
                IdCodec.decode(userId), IdCodec.decode(id), req));
    }

    @Operation(summary = "撤销待处理反馈")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> cancel(@CurrentUserId String userId, @PathVariable("id") String id) {
        feedbackService.cancelMy(IdCodec.decode(userId), IdCodec.decode(id));
        return ApiResponse.success();
    }
}
