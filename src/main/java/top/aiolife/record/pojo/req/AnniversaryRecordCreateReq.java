package top.aiolife.record.pojo.req;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import lombok.Data;

/** AnniversaryRecordCreateReq：仅包含接口允许写入的字段。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AnniversaryRecordCreateReq {
    /** 是否固定到首页：0=否，1=是。 */
    @Min(0)
    @Max(1)
    private Integer isPinned;


    private String title;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate targetDate;

    private String type;

    private String note;

    private String color;

    private String icon;
}
