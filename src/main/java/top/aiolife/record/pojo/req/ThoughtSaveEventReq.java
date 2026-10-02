package top.aiolife.record.pojo.req;

import lombok.Data;

/**
 * 想法关联事件保存请求体
 *
 * @author GPT
 * @date 2026/04/25
 */
@Data
public class ThoughtSaveEventReq {

    @jakarta.validation.constraints.NotBlank(message = "关联事件内容不能为空")
    private String content;
}
