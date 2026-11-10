package top.aiolife.feedback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import top.aiolife.core.query.QueryOrder;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.aiolife.core.query.CommonQuery;
import top.aiolife.core.resq.PageResp;
import top.aiolife.feedback.mapper.FeedbackCommentMapper;
import top.aiolife.feedback.mapper.FeedbackMapper;
import top.aiolife.feedback.pojo.entity.FeedbackCommentEntity;
import top.aiolife.feedback.pojo.entity.FeedbackEntity;
import top.aiolife.feedback.pojo.enums.FeedbackRoleType;
import top.aiolife.feedback.pojo.enums.FeedbackStatus;
import top.aiolife.feedback.pojo.query.FeedbackAdminQuery;
import top.aiolife.feedback.pojo.req.FeedbackBatchReq;
import top.aiolife.feedback.pojo.req.FeedbackCommentCreateReq;
import top.aiolife.feedback.pojo.req.FeedbackCreateReq;
import top.aiolife.feedback.pojo.req.FeedbackStatusUpdateReq;
import top.aiolife.feedback.pojo.vo.FeedbackCommentVO;
import top.aiolife.feedback.pojo.vo.FeedbackDetailVO;
import top.aiolife.feedback.pojo.vo.FeedbackVO;
import top.aiolife.feedback.service.IFeedbackService;
import top.aiolife.record.notification.SystemNotificationSender;
import top.aiolife.record.notification.NotificationRequest;
import top.aiolife.record.pojo.vo.FileVO;
import top.aiolife.record.service.IFileService;
import top.aiolife.record.service.FeishuNotificationService;
import top.aiolife.sso.mapper.UserMapper;
import top.aiolife.sso.pojo.entity.UserEntity;
import top.aiolife.system.service.ISystemConfigService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 反馈服务实现
 *
 * @author Ethan
 * @date 2026/07/19
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackServiceImpl implements IFeedbackService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int SUMMARY_LENGTH = 80;
    private static final String BIZ_TYPE_FEEDBACK = "feedback";
    private static final String BIZ_TYPE_COMMENT = "feedback_comment";
    private static final String NOTIFY_ADMIN_IDS_KEY = "feedback.notify_admin_ids";

    private final FeedbackMapper feedbackMapper;
    private final FeedbackCommentMapper commentMapper;
    private final IFileService fileService;
    private final ISystemConfigService systemConfigService;
    private final SystemNotificationSender notificationSender;
    private final FeishuNotificationService feishuNotificationService;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    // ========================= 用户侧 =========================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FeedbackVO create(long userId, FeedbackCreateReq req) {
        FeedbackEntity entity = new FeedbackEntity();
        entity.setUserId(userId);
        entity.setTitle(req.getTitle());
        entity.setContent(req.getContent());
        entity.setFeedbackType(req.getFeedbackType());
        entity.setStatus(FeedbackStatus.PENDING.name());
        entity.setPriority(req.getPriority() != null ? req.getPriority() : "MEDIUM");
        entity.fillCreateCommonField(userId);
        feedbackMapper.insert(entity);

        // 绑定附件
        if (req.getFileIds() != null && !req.getFileIds().isEmpty()) {
            fileService.bindBizId(req.getFileIds(), BIZ_TYPE_FEEDBACK, entity.getId());
        }

        // 通知管理员
        notifyAdmins(userId, entity.getId(), "create:" + entity.getId(), entity.getTitle());

        return toVO(entity, userId);
    }

    @Override
    public PageResp<FeedbackVO> listMy(long userId, CommonQuery<FeedbackAdminQuery> query) {
        int page = query.getPage() != null ? query.getPage() : 1;
        int size = query.getPageSize() != null ? query.getPageSize() : 20;
        FeedbackAdminQuery condition = query.getCondition();

        LambdaQueryWrapper<FeedbackEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FeedbackEntity::getUserId, userId);
        wrapper.eq(FeedbackEntity::getIsDeleted, 0);
        applyCommonFilters(wrapper, condition);
        QueryOrder.descNullsLast(wrapper, FeedbackEntity::getCreateTime);

        Page<FeedbackEntity> pageObj = new Page<>(page, size);
        var iPage = feedbackMapper.selectPage(pageObj, wrapper);

        List<FeedbackVO> voList = iPage.getRecords().stream()
                .map(e -> toVO(e, userId))
                .toList();
        return PageResp.of(voList, (int) iPage.getTotal());
    }

    @Override
    public FeedbackDetailVO getMyDetail(long userId, long feedbackId) {
        FeedbackEntity entity = feedbackMapper.selectById(feedbackId);
        if (entity == null || entity.getIsDeleted() == 1 || !entity.getUserId().equals(userId)) {
            throw new IllegalArgumentException("反馈不存在");
        }
        return toDetailVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FeedbackCommentVO addComment(long userId, long feedbackId, FeedbackCommentCreateReq req) {
        FeedbackEntity feedback = loadMyFeedback(userId, feedbackId);

        FeedbackCommentEntity comment = new FeedbackCommentEntity();
        comment.setFeedbackId(feedbackId);
        comment.setUserId(userId);
        comment.setRoleType(FeedbackRoleType.USER.name());
        comment.setContent(req.getContent());
        comment.fillCreateCommonField(userId);
        commentMapper.insert(comment);

        if (req.getFileIds() != null && !req.getFileIds().isEmpty()) {
            fileService.bindBizId(req.getFileIds(), BIZ_TYPE_COMMENT, comment.getId());
        }

        // 通知管理员
        notifyAdmins(userId, feedbackId, "comment:" + comment.getId(), feedback.getTitle());

        return toCommentVO(comment, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelMy(long userId, long feedbackId) {
        FeedbackEntity entity = loadMyFeedback(userId, feedbackId);
        if (!FeedbackStatus.PENDING.name().equals(entity.getStatus())) {
            throw new IllegalStateException("只有待处理状态的反馈可以撤销");
        }
        LambdaUpdateWrapper<FeedbackEntity> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(FeedbackEntity::getId, feedbackId)
                .set(FeedbackEntity::getIsDeleted, 1)
                .set(FeedbackEntity::getUpdateTime, LocalDateTime.now())
                .set(FeedbackEntity::getUpdateUser, userId);
        feedbackMapper.update(null, wrapper);
    }

    // ========================= 管理员侧 =========================

    @Override
    public PageResp<FeedbackVO> listAdmin(CommonQuery<FeedbackAdminQuery> query) {
        int page = query.getPage() != null ? query.getPage() : 1;
        int size = query.getPageSize() != null ? query.getPageSize() : 20;
        FeedbackAdminQuery condition = query.getCondition();

        LambdaQueryWrapper<FeedbackEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FeedbackEntity::getIsDeleted, 0);
        applyCommonFilters(wrapper, condition);
        QueryOrder.descNullsLast(wrapper, FeedbackEntity::getCreateTime);

        Page<FeedbackEntity> pageObj = new Page<>(page, size);
        var iPage = feedbackMapper.selectPage(pageObj, wrapper);

        List<FeedbackVO> voList = iPage.getRecords().stream()
                .map(e -> toVO(e, e.getUserId()))
                .toList();
        return PageResp.of(voList, (int) iPage.getTotal());
    }

    @Override
    public FeedbackDetailVO getAdminDetail(long feedbackId) {
        FeedbackEntity entity = feedbackMapper.selectById(feedbackId);
        if (entity == null || entity.getIsDeleted() == 1) {
            throw new IllegalArgumentException("反馈不存在");
        }
        return toDetailVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FeedbackCommentVO adminReply(long adminId, long feedbackId, FeedbackCommentCreateReq req) {
        FeedbackEntity feedback = loadFeedback(feedbackId);

        FeedbackCommentEntity comment = new FeedbackCommentEntity();
        comment.setFeedbackId(feedbackId);
        comment.setUserId(adminId);
        comment.setRoleType(FeedbackRoleType.ADMIN.name());
        comment.setContent(req.getContent());
        comment.fillCreateCommonField(adminId);
        commentMapper.insert(comment);

        if (req.getFileIds() != null && !req.getFileIds().isEmpty()) {
            fileService.bindBizId(req.getFileIds(), BIZ_TYPE_COMMENT, comment.getId());
        }

        // 通知反馈人
        notifyUser(feedback.getUserId(), feedbackId, "reply:" + comment.getId(), feedback.getTitle());

        return toCommentVO(comment, adminId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FeedbackVO adminUpdateStatus(long adminId, long feedbackId, FeedbackStatusUpdateReq req) {
        FeedbackEntity feedback = loadFeedback(feedbackId);

        LambdaUpdateWrapper<FeedbackEntity> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(FeedbackEntity::getId, feedbackId)
                .set(FeedbackEntity::getStatus, req.getStatus())
                .set(FeedbackEntity::getUpdateTime, LocalDateTime.now())
                .set(FeedbackEntity::getUpdateUser, adminId);
        feedbackMapper.update(null, wrapper);

        feedback.setStatus(req.getStatus());

        // 通知反馈人
        notifyUser(feedback.getUserId(), feedbackId,
                "status:" + feedbackId + ":" + req.getStatus(), feedback.getTitle());

        return toVO(feedback, feedback.getUserId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adminBatch(long adminId, FeedbackBatchReq req) {
        if (req.getIdList() == null || req.getIdList().isEmpty()) {
            return;
        }
        String newStatus = "CLOSE".equals(req.getAction()) ? FeedbackStatus.CLOSED.name() : req.getAction();

        LambdaUpdateWrapper<FeedbackEntity> wrapper = new LambdaUpdateWrapper<>();
        wrapper.in(FeedbackEntity::getId, req.getIdList())
                .eq(FeedbackEntity::getIsDeleted, 0)
                .set(FeedbackEntity::getStatus, newStatus)
                .set(FeedbackEntity::getUpdateTime, LocalDateTime.now())
                .set(FeedbackEntity::getUpdateUser, adminId);
        feedbackMapper.update(null, wrapper);
    }

    // ========================= 通知 =========================

    /**
     * 通知管理员：有新反馈 / 新评论
     */
    private void notifyAdmins(long fromUserId, long feedbackId, String eventKey, String feedbackTitle) {
        try {
            String json = systemConfigService.getValueByKey(NOTIFY_ADMIN_IDS_KEY);
            if (!StringUtils.hasText(json)) {
                return;
            }
            List<String> adminIds = objectMapper.readValue(json, new TypeReference<List<String>>() {});
            if (adminIds.isEmpty()) {
                return;
            }

            String fromName = getUserName(fromUserId);
            String title = "新反馈通知：" + feedbackTitle;
            String content = fromName + " 提交了新的反馈，请及时查看。";

            for (String adminIdStr : adminIds) {
                try {
                    long adminId = Long.parseLong(adminIdStr);
                    if (adminId == fromUserId) {
                        continue; // 不通知自己
                    }
                    UserEntity admin = userMapper.selectById(adminId);
                    if (admin != null && admin.getIsDeleted() == 0) {
                        notificationSender.send(admin, title, content, content);
                        feishuNotificationService.sendIfEnabled(new NotificationRequest(
                                adminId,
                                "FEEDBACK_ADMIN",
                                title,
                                content,
                                null,
                                "feedback:" + feedbackId + ":" + eventKey + ":" + adminId
                        ));
                    }
                } catch (Exception e) {
                    log.warn("通知管理员 {} 失败", adminIdStr, e);
                }
            }
        } catch (Exception e) {
            log.warn("获取通知管理员列表失败", e);
        }
    }

    /**
     * 通知反馈人：管理员已回复 / 状态已变更
     */
    private void notifyUser(long userId, long feedbackId, String eventKey, String feedbackTitle) {
        try {
            UserEntity user = userMapper.selectById(userId);
            if (user == null || user.getIsDeleted() != 0) {
                return;
            }
            String title = "反馈回复通知：" + feedbackTitle;
            String content = "您的反馈「" + feedbackTitle + "」有了新的回复，请查看。";
            notificationSender.send(user, title, content, content);
            feishuNotificationService.sendIfEnabled(new NotificationRequest(
                    userId,
                    "FEEDBACK_REPLY",
                    title,
                    content,
                    null,
                    "feedback:" + feedbackId + ":" + eventKey + ":" + userId
            ));
        } catch (Exception e) {
            log.warn("通知用户 {} 失败", userId, e);
        }
    }

    // ========================= 私有辅助 =========================

    private FeedbackEntity loadMyFeedback(long userId, long feedbackId) {
        FeedbackEntity entity = feedbackMapper.selectById(feedbackId);
        if (entity == null || entity.getIsDeleted() == 1 || !entity.getUserId().equals(userId)) {
            throw new IllegalArgumentException("反馈不存在");
        }
        return entity;
    }

    private FeedbackEntity loadFeedback(long feedbackId) {
        FeedbackEntity entity = feedbackMapper.selectById(feedbackId);
        if (entity == null || entity.getIsDeleted() == 1) {
            throw new IllegalArgumentException("反馈不存在");
        }
        return entity;
    }

    private void applyCommonFilters(LambdaQueryWrapper<FeedbackEntity> wrapper, FeedbackAdminQuery condition) {
        if (condition == null) {
            return;
        }
        if (StringUtils.hasText(condition.getStatus())) {
            wrapper.eq(FeedbackEntity::getStatus, condition.getStatus());
        }
        if (StringUtils.hasText(condition.getFeedbackType())) {
            wrapper.eq(FeedbackEntity::getFeedbackType, condition.getFeedbackType());
        }
        if (condition.getUserId() != null) {
            wrapper.eq(FeedbackEntity::getUserId, condition.getUserId());
        }
        if (StringUtils.hasText(condition.getKeyword())) {
            wrapper.and(w -> w.like(FeedbackEntity::getTitle, condition.getKeyword())
                    .or()
                    .like(FeedbackEntity::getContent, condition.getKeyword()));
        }
        if (StringUtils.hasText(condition.getStartTime())) {
            wrapper.ge(FeedbackEntity::getCreateTime, condition.getStartTime());
        }
        if (StringUtils.hasText(condition.getEndTime())) {
            wrapper.le(FeedbackEntity::getCreateTime, condition.getEndTime());
        }
    }

    private FeedbackVO toVO(FeedbackEntity entity, long userId) {
        FeedbackVO vo = new FeedbackVO();
        vo.setId(String.valueOf(entity.getId()));
        vo.setUserId(entity.getUserId());
        vo.setUserName(getUserName(entity.getUserId()));
        vo.setTitle(entity.getTitle());
        vo.setSummary(truncate(entity.getContent(), SUMMARY_LENGTH));
        vo.setFeedbackType(entity.getFeedbackType());
        vo.setStatus(entity.getStatus());
        vo.setPriority(entity.getPriority());
        vo.setFiles(fileService.getByBiz(BIZ_TYPE_FEEDBACK, entity.getId()));
        vo.setCommentCount(countComments(entity.getId()));
        vo.setCreateTime(entity.getCreateTime() == null ? null : entity.getCreateTime().format(FORMATTER));
        vo.setUpdateTime(entity.getUpdateTime() == null ? null : entity.getUpdateTime().format(FORMATTER));
        return vo;
    }

    private FeedbackDetailVO toDetailVO(FeedbackEntity entity) {
        FeedbackDetailVO vo = new FeedbackDetailVO();
        vo.setId(String.valueOf(entity.getId()));
        vo.setUserId(entity.getUserId());
        vo.setUserName(getUserName(entity.getUserId()));
        vo.setTitle(entity.getTitle());
        vo.setContent(entity.getContent());
        vo.setSummary(truncate(entity.getContent(), SUMMARY_LENGTH));
        vo.setFeedbackType(entity.getFeedbackType());
        vo.setStatus(entity.getStatus());
        vo.setPriority(entity.getPriority());
        vo.setFiles(fileService.getByBiz(BIZ_TYPE_FEEDBACK, entity.getId()));
        vo.setCommentCount(countComments(entity.getId()));
        vo.setCreateTime(entity.getCreateTime() == null ? null : entity.getCreateTime().format(FORMATTER));
        vo.setUpdateTime(entity.getUpdateTime() == null ? null : entity.getUpdateTime().format(FORMATTER));

        // 加载评论
        LambdaQueryWrapper<FeedbackCommentEntity> cw = new LambdaQueryWrapper<>();
        cw.eq(FeedbackCommentEntity::getFeedbackId, entity.getId());
        cw.eq(FeedbackCommentEntity::getIsDeleted, 0);
        QueryOrder.ascNullsFirst(cw, FeedbackCommentEntity::getCreateTime);
        List<FeedbackCommentEntity> comments = commentMapper.selectList(cw);
        vo.setComments(comments.stream()
                .map(c -> toCommentVO(c, c.getUserId()))
                .toList());

        return vo;
    }

    private FeedbackCommentVO toCommentVO(FeedbackCommentEntity comment, long userId) {
        FeedbackCommentVO vo = new FeedbackCommentVO();
        vo.setId(String.valueOf(comment.getId()));
        vo.setUserId(comment.getUserId());
        vo.setUserName(getUserName(comment.getUserId()));
        vo.setRoleType(comment.getRoleType());
        vo.setContent(comment.getContent());
        vo.setFiles(fileService.getByBiz(BIZ_TYPE_COMMENT, comment.getId()));
        vo.setCreateTime(comment.getCreateTime() == null ? null : comment.getCreateTime().format(FORMATTER));
        return vo;
    }

    private int countComments(long feedbackId) {
        LambdaQueryWrapper<FeedbackCommentEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FeedbackCommentEntity::getFeedbackId, feedbackId);
        wrapper.eq(FeedbackCommentEntity::getIsDeleted, 0);
        return Math.toIntExact(commentMapper.selectCount(wrapper));
    }

    private String getUserName(long userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            return "未知用户";
        }
        return StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername();
    }

    private String truncate(String text, int maxLen) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        // 去除 Markdown 标记和换行
        String clean = text.replaceAll("[#*`>\\-\\n\\r]", " ").replaceAll("\\s+", " ").trim();
        if (clean.length() <= maxLen) {
            return clean;
        }
        return clean.substring(0, maxLen) + "...";
    }
}
