package top.aiolife.record.pojo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import lombok.Data;

/** AnniversaryRecordVO：仅包含接口需要返回的字段。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AnniversaryRecordVO {
    /** 是否固定到首页：0=否，1=是。 */
    private Integer isPinned;

    /** 首页固定顺序，越小越靠前。 */
    private Integer pinnedSort;


    private Long id;

    private String title;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate targetDate;

    private String type;

    private String note;

    private String color;

    private String icon;
}
