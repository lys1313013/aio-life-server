package top.aiolife.system.api;

import io.swagger.v3.oas.annotations.Operation;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.sso.service.IUserService;
import top.aiolife.system.pojo.req.MenuSaveReq;
import top.aiolife.system.pojo.req.MenuSortUpdateReq;
import top.aiolife.system.pojo.req.MenuVisibilityUpdateReq;
import top.aiolife.system.pojo.vo.MenuAdminVO;
import top.aiolife.system.service.IMenuService;

/**
 * 菜单管理控制器
 *
 * <p>用途：管理员维护系统菜单配置（树形结构、路由与 meta、角色可见性等）。</p>
 *
 * @author Ethan
 * @date 2026/04/19
 */
@RestController
@RequestMapping("/menu/admin")
@RequiredArgsConstructor
@SaCheckRole("admin")
public class MenuAdminController {

    private final IMenuService menuService;

    private final IUserService userService;

    /**
     * 获取菜单树（管理端）。
     *
     * @return 统一返回结构，data 为菜单树
     */
    @Operation(summary = "管理员查询菜单树")
    @GetMapping("/tree")
    public ApiResponse<List<MenuAdminVO>> tree() {
        return ApiResponse.success(menuService.getAdminMenuTree());
    }

    /**
     * 获取角色选项列表（用于菜单 Roles 下拉选择）。
     *
     * <p>用途：为菜单管理页提供可选角色集合，避免前端写死或手工输入。</p>
     *
     * @return 统一返回结构，data 为去重后的角色字符串数组
     */
    @Operation(summary = "查询菜单可选角色")
    @GetMapping("/role-options")
    public ApiResponse<List<String>> roleOptions() {
        return ApiResponse.success(userService.listRoleOptions());
    }

    /**
     * 新增菜单节点。
     *
     * @param req 菜单保存请求体
     * @return 统一返回结构，data 为新增后的菜单节点
     */
    @Operation(summary = "管理员新增菜单")
    @PostMapping
    public ApiResponse<MenuAdminVO> create(@RequestBody MenuSaveReq req) throws Exception {
        long userId = StpUtil.getLoginIdAsLong();
        return ApiResponse.success(menuService.create(req, userId));
    }

    /**
     * 更新菜单节点。
     *
     * @param id 菜单ID
     * @param req 菜单保存请求体
     * @return 统一返回结构，data 为更新后的菜单节点
     */
    @Operation(summary = "管理员更新菜单")
    @PutMapping("/{id}")
    public ApiResponse<MenuAdminVO> update(@PathVariable long id, @RequestBody MenuSaveReq req) throws Exception {
        long userId = StpUtil.getLoginIdAsLong();
        return ApiResponse.success(menuService.update(id, req, userId));
    }

    /** 更新菜单展示端位掩码。 */
    @Operation(summary = "管理员设置菜单展示端")
    @PutMapping("/{id}/visible-clients")
    public ApiResponse<MenuAdminVO> updateVisibleClients(@PathVariable long id,
            @Valid @RequestBody MenuVisibilityUpdateReq body) {
        return ApiResponse.success(menuService.updateVisibleClients(id, body.getVisibleClients(), StpUtil.getLoginIdAsLong()));
    }

    /** 更新菜单排序。 */
    @Operation(summary = "管理员调整菜单排序")
    @PutMapping("/{id}/sort")
    public ApiResponse<MenuAdminVO> updateSort(@PathVariable long id, @Valid @RequestBody MenuSortUpdateReq body) throws Exception {
        long userId = StpUtil.getLoginIdAsLong();
        return ApiResponse.success(menuService.updateSort(id, body.getSortOrder(), userId));
    }

    /**
     * 删除菜单节点。
     *
     * @param id 菜单ID
     * @return 统一返回结构
     */
    @Operation(summary = "管理员删除菜单")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable long id) {
        long userId = StpUtil.getLoginIdAsLong();
        menuService.delete(id, userId);
        return ApiResponse.success();
    }

}
