package top.aiolife.sso.pojo.req;

import lombok.Data;

import java.util.List;

/**
 * 保存用户二级锁菜单请求
 *
 * @author Lys
 * @date 2026/07/25
 */
@Data
public class SaveSecondaryLockMenusReq {

    private List<Long> menuIds;
}
