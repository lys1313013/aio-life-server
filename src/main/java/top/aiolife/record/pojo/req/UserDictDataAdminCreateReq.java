package top.aiolife.record.pojo.req;

import com.fasterxml.jackson.annotation.JsonInclude;
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
    private Integer dictSort;

    /**
     * 字典标签(分类名称)
     */
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
     * 特定分类所需的额外扩展字段(JSON)
     */
    private String extData;

    /**
     * 是否默认（Y是 N否）
     */
    private String isDefault;

    /**
     * 状态（0正常 1停用）
     */
    private String status;

    /**
     * 是否只读（Y是 N否），用于限制普通用户修改公共分类
     */
    private String isReadonly;

    /**
     * 备注
     */
    private String remark;
}
