package top.aiolife.feedback.api;

import java.util.ArrayList;
import java.util.List;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.aiolife.core.query.CommonQuery;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.resq.PageResp;
import top.aiolife.feedback.pojo.query.FeedbackAdminQuery;
import top.aiolife.feedback.pojo.req.FeedbackBatchReq;
import top.aiolife.feedback.pojo.req.FeedbackCommentCreateReq;
import top.aiolife.feedback.pojo.req.FeedbackStatusUpdateReq;
import top.aiolife.feedback.pojo.vo.AdminUserVO;
import top.aiolife.feedback.pojo.vo.FeedbackCommentVO;
import top.aiolife.feedback.pojo.vo.FeedbackDetailVO;
import top.aiolife.feedback.pojo.vo.FeedbackVO;
import top.aiolife.feedback.service.IFeedbackService;
import top.aiolife.sso.mapper.UserMapper;
import top.aiolife.sso.pojo.entity.UserEntity;

/**
 * 反馈管理控制器（管理员）
 *
 * @author Ethan
 * @date 2026/07/19
 */
@RestController
@RequestMapping("/feedback/admin")
@RequiredArgsConstructor
@SaCheckRole("admin")
public class FeedbackAdminController {

    private final IFeedbackService feedbackService;

    private final UserMapper userMapper;

    /**
     * 全部反馈列表（分页 + 筛选）
     */
    @GetMapping("/list")
    public ApiResponse<PageResp<FeedbackVO>> list(CommonQuery<FeedbackAdminQuery> query) {
        return ApiResponse.success(feedbackService.listAdmin(query));
    }

    /**
     * 反馈详情（含评论）
     */
    @GetMapping("/{id}")
    public ApiResponse<FeedbackDetailVO> detail(@PathVariable("id") long id) {
        return ApiResponse.success(feedbackService.getAdminDetail(id));
    }

    /**
     * 管理员回复
     */
    @PostMapping("/{id}/reply")
    public ApiResponse<FeedbackCommentVO> reply(@PathVariable("id") long id,
                                                 @RequestBody FeedbackCommentCreateReq req) {
        long adminId = StpUtil.getLoginIdAsLong();
        return ApiResponse.success(feedbackService.adminReply(adminId, id, req));
    }

    /**
     * 变更状态
     */
    @PutMapping("/{id}/status")
    public ApiResponse<FeedbackVO> updateStatus(@PathVariable("id") long id,
                                                 @RequestBody FeedbackStatusUpdateReq req) {
        long adminId = StpUtil.getLoginIdAsLong();
        return ApiResponse.success(feedbackService.adminUpdateStatus(adminId, id, req));
    }

    /**
     * 批量操作
     */
    @PostMapping("/batch")
    public ApiResponse<Void> batch(@RequestBody FeedbackBatchReq req) {
        long adminId = StpUtil.getLoginIdAsLong();
        feedbackService.adminBatch(adminId, req);
        return ApiResponse.success();
    }

    /**
     * 管理员用户列表（用于配置通知接收人下拉）
     */
    @GetMapping("/admin-users")
    public ApiResponse<List<AdminUserVO>> adminUsers() {
        LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(UserEntity::getRole, "admin")
                .eq(UserEntity::getIsDeleted, 0)
                .orderByAsc(UserEntity::getId);
        List<UserEntity> users = userMapper.selectList(wrapper);
        List<AdminUserVO> result = new ArrayList<>();
        for (UserEntity u : users) {
            AdminUserVO vo = new AdminUserVO();
            vo.setId(String.valueOf(u.getId()));
            vo.setUsername(u.getUsername());
            vo.setNickname(u.getNickname());
            result.add(vo);
        }
        return ApiResponse.success(result);
    }
}
