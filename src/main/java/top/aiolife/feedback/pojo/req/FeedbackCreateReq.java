package top.aiolife.feedback.pojo.req;

import jakarta.validation.constraints.Pattern;
import top.aiolife.core.id.AttachmentKey;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 创建反馈请求
 *
 * @author Ethan
 * @date 2026/07/19
 */
@Getter
@Setter
public class FeedbackCreateReq {

    /**
     * 标题，≤200 字
     */
    private String title;

    /**
     * 内容（Markdown）
     */
    private String content;

    /**
     * BUG / SUGGESTION / QUESTION / OTHER
     */
    private String feedbackType;

    /**
     * LOW / MEDIUM / HIGH（一期可不开放）
     */
    private String priority;

    /**
     * 上传后返回的文件 ID 列表
     */
    private List<@Pattern(regexp = AttachmentKey.PATTERN) String> fileIds;
}
