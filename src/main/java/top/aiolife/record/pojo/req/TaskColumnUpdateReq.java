package top.aiolife.record.pojo.req;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/** TaskColumnUpdateReq：仅包含接口允许写入的字段。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TaskColumnUpdateReq {

    /**
     * 列标题
     */
    private String title;

    /**
     * 排序
     */
    private Integer sortOrder;

    /**
     * 背景颜色
     */
    private String bgColor;
}
