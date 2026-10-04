package top.aiolife.system.pojo.req;

import lombok.Data;

import java.util.List;

/**
 * 快捷导航整块保存请求体
 *
 * <p>items 允许为空数组（= 清空当前端），两端合计不允许超过 12 项。校验由 Service 层负责。</p>
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

        private Integer enabled;
    }
}
