package top.aiolife.record.pojo.query;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/** UserDictDataQuery：仅包含接口支持筛选的字段。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserDictDataQuery {

    /**
     * 字典类型
     */
    private String dictType;

    /**
     * 字典标签(分类名称)
     */
    private String dictLabel;

    /**
     * 状态（0正常 1停用）
     */
    private String status;
}
