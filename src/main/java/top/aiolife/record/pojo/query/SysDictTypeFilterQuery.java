package top.aiolife.record.pojo.query;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/** SysDictTypeFilterQuery：仅包含接口支持筛选的字段。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SysDictTypeFilterQuery {

    /**
     * 字典名称
     */
    private String dictName;
}
