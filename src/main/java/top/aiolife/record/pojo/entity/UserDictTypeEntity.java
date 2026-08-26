package top.aiolife.record.pojo.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 用户字典类型实体类 (已转为虚拟实体)
 *
 * @author Lys
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserDictTypeEntity {

    /**
     * 字典主键
     */
    private Long id;

    /**
     * 所属用户ID
     */
    private Long userId;

    /**
     * 字典名称
     */
    private String dictName;

    /**
     * 字典类型
     */
    private String dictType;

    /**
     * 状态（0正常 1停用）
     */
    private String status;

    /**
     * 备注
     */
    private String remark;

}
