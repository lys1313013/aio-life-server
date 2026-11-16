package top.aiolife.feedback.pojo.req;

import jakarta.validation.constraints.Pattern;
import top.aiolife.core.id.AttachmentKey;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 反馈评论创建请求（用户追加 / 管理员回复）
 *
 * @author Ethan
 * @date 2026/07/19
 */
@Getter
@Setter
public class FeedbackCommentCreateReq {

    /**
     * 评论内容（Markdown）
     */
    private String content;

    /**
     * 上传后返回的文件 ID 列表
     */
    private List<@Pattern(regexp = AttachmentKey.PATTERN) String> fileIds;
}
