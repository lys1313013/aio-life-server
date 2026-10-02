package top.aiolife.record.pojo.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/** TaskColumnVO：仅包含接口需要返回的字段。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TaskColumnVO {

    private Long id;

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
