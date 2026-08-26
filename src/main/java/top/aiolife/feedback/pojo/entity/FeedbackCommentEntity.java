package top.aiolife.feedback.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import top.aiolife.core.pojo.entity.BaseEntity;

/**
 * 反馈评论
 *
 * <p>用户追加反馈和管理员回复统一存储。通过 roleType 区分身份。</p>
 *
 * @author Ethan
 * @date 2026/07/19
 */
@Getter
@Setter
@TableName("feedback_comment")
public class FeedbackCommentEntity extends BaseEntity {

    private Long feedbackId;

    private Long userId;

    /**
     * USER / ADMIN
     */
    private String roleType;

    private String content;
}
