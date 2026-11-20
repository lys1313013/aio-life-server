package top.aiolife.record.pojo.req;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/** UserDictDataAdminCreateReq：仅包含接口允许写入的字段。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserDictDataAdminCreateReq {

    /**
     * 字典类型
     */
    private String dictType;

    /**
     * 字典排序
     */
    private Integer sortOrder;

    /**
     * 字典标签(分类名称)
     */
    @Pattern(regexp = "(?s).*\\S.*", message = "字典标签不能是空白")
    private String dictLabel;

    /**
     * 字典键值(分类标识)
     */
    private String dictValue;

    /**
     * 颜色值(Hex)
     */
    private String color;

    /**
     * 图标名称(Iconify格式)
     */
    private String icon;

    /**
     * 字典项的补充属性（JSON 字符串）
     */
    private String extraData;

    /**
     * 是否默认值：1=是，0=否
     */
    private Integer isDefault;

    /**
     * 是否启用：1=启用，0=停用
     */
    @Min(0)
    @Max(1)
    private Integer isEnabled;

    /**
     * 是否只读：1=是，0=否；用于限制普通用户修改公共分类
     */
    private Integer isReadonly;

    /**
     * 备注
     */
    private String remark;
}
