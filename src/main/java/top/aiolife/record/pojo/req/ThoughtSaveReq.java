package top.aiolife.record.pojo.req;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 想法保存请求体
 *
 * @author GPT
 * @date 2026/04/25
 */
@Data
public class ThoughtSaveReq {

    @NotBlank(message = "闪念内容不能为空")
    @Schema(description = "闪念正文")
    private String content;

    @Min(0)
    @Max(1)
    @Schema(description = "是否固定到首页：0=否，1=是")
    private Integer isPinned;

    @Valid
    private List<@NotNull ThoughtSaveEventReq> events;
}
