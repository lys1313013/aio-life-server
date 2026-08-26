package top.aiolife.feedback.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import top.aiolife.core.pojo.entity.BaseEntity;

/**
 * 用户反馈主表
 *
 * @author Ethan
 * @date 2026/07/19
 */
@Getter
@Setter
@TableName("feedback")
public class FeedbackEntity extends BaseEntity {

    private Long userId;

    private String title;

    private String content;

    /**
     * BUG / SUGGESTION / QUESTION / OTHER
     */
    private String feedbackType;

    /**
     * PENDING / PROCESSING / RESOLVED / CLOSED / REJECTED
     */
    private String status;

    /**
     * LOW / MEDIUM / HIGH（预留）
     */
    private String priority;
}
