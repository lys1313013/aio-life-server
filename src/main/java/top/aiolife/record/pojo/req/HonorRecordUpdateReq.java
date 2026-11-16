package top.aiolife.record.pojo.req;

import jakarta.validation.constraints.Pattern;
import top.aiolife.core.id.AttachmentKey;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import lombok.Data;

/** HonorRecordUpdateReq：仅包含接口允许写入的字段。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HonorRecordUpdateReq {

    @NotNull
    private Long id;

    private String title;

    private String description;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate honorDate;

    private String issuer;

    private String level;

    private Long categoryId;

    private String customCategory;

    /** 标签，JSON 数组文本 */
    @Size(max = 500)
    private String tags;

    private Integer isTop;

    private Integer isPublic;

    private Integer sortOrder;

    private List<@Pattern(regexp = AttachmentKey.PATTERN) String> fileIds;
}
