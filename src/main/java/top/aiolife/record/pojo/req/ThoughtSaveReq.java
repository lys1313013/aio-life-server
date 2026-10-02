package top.aiolife.record.pojo.req;

import lombok.Data;

import java.util.List;

/**
 * 想法保存请求体
 *
 * @author GPT
 * @date 2026/04/25
 */
@Data
public class ThoughtSaveReq {

    @jakarta.validation.constraints.NotBlank(message = "闪念内容不能为空")
    private String content;

    @jakarta.validation.constraints.Min(0)
    @jakarta.validation.constraints.Max(1)
    private Integer isPinned;

    @jakarta.validation.Valid
    private List<@jakarta.validation.constraints.NotNull ThoughtSaveEventReq> events;
}
