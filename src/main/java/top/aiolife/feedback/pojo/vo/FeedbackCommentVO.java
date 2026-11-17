package top.aiolife.feedback.pojo.vo;

import lombok.Getter;
import lombok.Setter;
import top.aiolife.record.pojo.vo.AttachmentVO;

import java.util.List;

/**
 * 反馈评论 VO
 *
 * @author Ethan
 * @date 2026/07/19
 */
@Getter
@Setter
public class FeedbackCommentVO {

    private String id;

    private Long userId;

    private String userName;

    /**
     * USER / ADMIN
     */
    private String roleType;

    private String content;

    private List<AttachmentVO> files;

    private String createTime;
}
