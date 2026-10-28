package top.aiolife.system.pojo.req;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import lombok.Data;

import java.util.List;

/**
 * 快捷导航整块保存请求体
 *
 * <p>items 允许为空数组（= 清空当前端），两端合计不允许超过 15 项。校验由 Service 层负责。</p>
 *
 * @author Ethan
 * @date 2026/06/05
 */
@Data
public class QuickNavSaveReq {

    private List<Item> items;

    @Data
    public static class Item {

        private Long menuId;

        private Integer sortOrder;

        @Min(0)
    @Max(1)
    private Integer isEnabled;
    }
}
