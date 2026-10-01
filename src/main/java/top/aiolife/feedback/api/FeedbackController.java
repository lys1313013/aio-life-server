package top.aiolife.feedback.api;

import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.aiolife.core.query.CommonQuery;
import top.aiolife.core.query.QueryParams;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.resq.PageResp;
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
@RestController
@RequestMapping("/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final IFeedbackService feedbackService;

    /**
     * 提交反馈
     */
    @PostMapping
    public ApiResponse<FeedbackVO> create(@RequestBody FeedbackCreateReq req) {
        long userId = StpUtil.getLoginIdAsLong();
        return ApiResponse.success(feedbackService.create(userId, req));
    }

    /**
     * 我的反馈列表
     */
    @GetMapping("/my")
    public ApiResponse<PageResp<FeedbackVO>> listMy(@QueryParams CommonQuery<FeedbackAdminQuery> query) {
        long userId = StpUtil.getLoginIdAsLong();
        return ApiResponse.success(feedbackService.listMy(userId, query));
    }

    /**
     * 我的反馈详情
     */
    @GetMapping("/my/{id}")
    public ApiResponse<FeedbackDetailVO> getMyDetail(@PathVariable("id") long id) {
        long userId = StpUtil.getLoginIdAsLong();
        return ApiResponse.success(feedbackService.getMyDetail(userId, id));
    }

    /**
     * 追加评论
     */
    @PostMapping("/{id}/comment")
    public ApiResponse<FeedbackCommentVO> addComment(@PathVariable("id") long id,
                                                     @RequestBody FeedbackCommentCreateReq req) {
        long userId = StpUtil.getLoginIdAsLong();
        return ApiResponse.success(feedbackService.addComment(userId, id, req));
    }

    /**
     * 撤销反馈（仅 PENDING）
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> cancel(@PathVariable("id") long id) {
        long userId = StpUtil.getLoginIdAsLong();
        feedbackService.cancelMy(userId, id);
        return ApiResponse.success();
    }

}
