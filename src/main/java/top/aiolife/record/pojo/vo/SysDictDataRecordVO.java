package top.aiolife.record.pojo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import lombok.Data;

/** SysDictDataRecordVO：仅包含接口需要返回的字段。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SysDictDataRecordVO {

    private Long id;

    /**
     * 所属字典类型ID
     */
    private Long dictTypeId;

    /**
     * 字典名称
     */
    private String dictName;

    /**
     * 字典类型
     */
    private String dictType;

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
    private Integer isEnabled;

    /**
     * 备注
     */
    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
