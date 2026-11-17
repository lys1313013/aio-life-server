package top.aiolife.record.pojo.req;

import jakarta.validation.constraints.Pattern;
import top.aiolife.core.id.AttachmentKey;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.util.List;
import lombok.Data;

/** HonorRecordCreateReq：仅包含接口允许写入的字段。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HonorRecordCreateReq {

    private String title;

    private String description;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate honorDate;

    private String issuer;

    private String level;

    private Long categoryId;

    private String customCategory;

    /** 标签数组 */
    private List<String> tags;

    private Integer isTop;

    private Integer isPublic;

    private Integer sortOrder;

    private List<@Pattern(regexp = AttachmentKey.PATTERN) String> fileIds;
}
