package top.aiolife.record.pojo.req;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** SysDictDataCreateReq：仅包含接口允许写入的字段。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SysDictDataCreateReq {

    /**
     * 字典ID
     */
    @NotNull
    private Long dictId;

    /**
     * 字典排序
     */
    private Integer sortOrder;

    /**
     * 字典标签
     */
    private String dictLabel;

    /**
     * 字典值
     */
    @NotBlank
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
     * 是否为默认值
     */
    private String isDefault;

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
