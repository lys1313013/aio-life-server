package top.aiolife.record.pojo.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/** HonorCategoryVO：仅包含接口需要返回的字段。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HonorCategoryVO {

    private Long id;

    private String name;

    private String icon;

    private String color;

    private Integer sortOrder;
}
