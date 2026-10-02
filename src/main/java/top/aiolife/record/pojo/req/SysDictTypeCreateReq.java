package top.aiolife.record.pojo.req;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/** SysDictTypeCreateReq：仅包含接口允许写入的字段。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SysDictTypeCreateReq {

    /**
     * 字典名称
     */
    private String dictName;

    /**
     * 字典类型
     */
    private String dictType;

    /**
     * 状态
     */
    private String status;

    /**
     * 备注
     */
    private String remark;
}
