package top.aiolife.record.pojo.req;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/** SysDictDataUpdateReq：仅包含接口允许写入的字段。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SysDictDataUpdateReq {

    /**
     * 所属字典类型ID
     */
    private Long dictTypeId;

    /**
     * 字典排序
     */
    private Integer sortOrder;

    /**
     * 字典标签
     */
    @Pattern(regexp = "(?s).*\\S.*", message = "字典标签不能是空白")
    private String dictLabel;

    /**
     * 字典值
     */
    @Pattern(regexp = "(?s).*\\S.*", message = "字典值不能是空白")
    private String dictValue;

    /**
     * CSS类名
     */
    private String cssClass;

    /**
     * 列表类名
     */
    private String listClass;

    /**
     * 是否默认值：1=是，0=否
     */
    private Integer isDefault;

    /**
     * 状态
     */
    @Min(0)
    @Max(1)
    private Integer isEnabled;

    /**
     * 备注
     */
    private String remark;
}
