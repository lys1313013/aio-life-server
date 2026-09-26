package top.aiolife.system.pojo.req;

import lombok.Data;

import java.util.List;

@Data
public class UserMenuHiddenSaveReq {
    /** 全量隐藏菜单 ID；空数组恢复默认，缺失字段不允许覆盖设置。 */
    private List<Long> menuIds;
}
