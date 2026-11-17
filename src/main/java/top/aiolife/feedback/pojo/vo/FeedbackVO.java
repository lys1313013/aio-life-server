package top.aiolife.feedback.pojo.vo;

import lombok.Getter;
import lombok.Setter;
import top.aiolife.record.pojo.vo.AttachmentVO;

import java.util.List;

/**
 * 反馈列表 VO
 *
 * @author Ethan
 * @date 2026/07/19
 */
@Getter
@Setter
public class FeedbackVO {

    private String id;

    private Long userId;

    private String userName;

    private String title;

    /**
     * 内容摘要（列表展示用，截取前 N 字）
     */
    private String summary;

    private String feedbackType;

    private String status;

    private String priority;

    private List<AttachmentVO> files;

    private Integer commentCount;

    private String createTime;

    private String updateTime;
}
