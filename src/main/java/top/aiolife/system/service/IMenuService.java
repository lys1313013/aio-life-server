package top.aiolife.system.service;

import top.aiolife.system.pojo.req.MenuSaveReq;
import top.aiolife.system.pojo.vo.MenuAdminVO;
import top.aiolife.system.pojo.vo.MenuRouteVO;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 菜单服务接口
 *
 * @author Ethan
 * @date 2026/04/19
 */
public interface IMenuService {

    /**
     * 获取当前用户可访问的菜单路由树。
     *
     * @param roles 当前用户角色列表
     * @return 菜单路由树
     */
    default List<MenuRouteVO> getAccessibleMenuTree(List<String> roles) {
        return getAccessibleMenuTree(roles, MenuClient.WEB);
    }

    List<MenuRouteVO> getAccessibleMenuTree(List<String> roles, MenuClient client);

    /**
     * 获取当前用户可访问的菜单 ID 集合（用于权限校验）。
     *
     * @param roles 当前用户角色列表
     * @return 菜单 ID 集合
     */
    default Set<Long> getAccessibleMenuIds(List<String> roles) {
        return getAccessibleMenuIds(roles, MenuClient.WEB);
    }

    Set<Long> getAccessibleMenuIds(List<String> roles, MenuClient client);

    /**
     * 获取当前用户可访问的"叶子菜单"扁平列表。
     *
     * <p>叶子判定：component 非空、不是 BasicLayout/IFrameView、meta.hideInMenu != true。
     * 返回的 Map 字段：menuId, title, icon, color, path, target。</p>
     *
     * @param roles 当前用户角色列表
     * @return 叶子菜单信息列表
     */
    default List<Map<String, Object>> listAccessibleLeaves(List<String> roles) {
        return listAccessibleLeaves(roles, MenuClient.WEB);
    }

    List<Map<String, Object>> listAccessibleLeaves(List<String> roles, MenuClient client);

    /**
     * 获取菜单树（管理端）。
     *
     * @return 全量菜单树
     */
    List<MenuAdminVO> getAdminMenuTree();

    /**
     * 新增菜单节点。
     *
     * @param req 菜单保存请求体
     * @param userId 操作人ID
     * @return 新增后的菜单节点
     * @throws Exception 参数或持久化异常
     */
    MenuAdminVO create(MenuSaveReq req, long userId) throws Exception;

    /**
     * 更新菜单节点。
     *
     * @param id 菜单ID
     * @param req 菜单保存请求体
     * @param userId 操作人ID
     * @return 更新后的菜单节点
     * @throws Exception 参数或持久化异常
     */
    MenuAdminVO update(long id, MenuSaveReq req, long userId) throws Exception;

    /**
     * 更新菜单启用状态。
     *
     * @param id 菜单ID
     * @param status 状态（1启用，0禁用）
     * @param userId 操作人ID
     * @return 更新后的菜单节点
     * @throws Exception 参数或持久化异常
     */
    MenuAdminVO updateStatus(long id, int status, long userId) throws Exception;

    MenuAdminVO updateMobileStatus(long id, int status, long userId) throws Exception;

    MenuAdminVO updateSort(long id, int sort, long userId) throws Exception;

    /**
     * 删除菜单节点（逻辑删除）。
     *
     * @param id 菜单ID
     * @param userId 操作人ID
     */
    void delete(long id, long userId);

}
