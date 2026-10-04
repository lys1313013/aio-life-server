package top.aiolife.record.pojo.req;

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

    private String tags;

    private Integer isTop;

    private Integer isPublic;

    private Integer sortOrder;

    private List<String> fileIds;
}
