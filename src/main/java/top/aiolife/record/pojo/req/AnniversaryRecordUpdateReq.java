package top.aiolife.record.pojo.req;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.Data;

/** AnniversaryRecordUpdateReq：仅包含接口允许写入的字段。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AnniversaryRecordUpdateReq {
    /** 是否固定到首页：0=否，1=是。 */
    @Min(0)
    @Max(1)
    private Integer isPinned;


    @NotNull
    private Long id;

    private String title;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate targetDate;

    private String type;

    private String note;

    private String color;

    private String icon;
}
