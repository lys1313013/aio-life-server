package top.aiolife.system.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import cn.dev33.satoken.annotation.SaCheckRole;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.aiolife.core.id.IdCodec;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.web.CurrentUserId;
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
@Tag(name = "菜单管理")
@RestController
@RequestMapping("/menu/admin")
@RequiredArgsConstructor
@SaCheckRole("admin")
public class MenuAdminController {

    private final IMenuService menuService;

    private final IUserService userService;

    @Operation(summary = "管理员查询菜单树")
    @GetMapping("/tree")
    public ApiResponse<List<MenuAdminVO>> tree() {
        return ApiResponse.success(menuService.getAdminMenuTree());
    }

    @Operation(summary = "查询菜单可选角色")
    @GetMapping("/role-options")
    public ApiResponse<List<String>> roleOptions() {
        return ApiResponse.success(userService.listRoleOptions());
    }

    @Operation(summary = "管理员新增菜单")
    @PostMapping
    public ApiResponse<MenuAdminVO> create(@CurrentUserId String userId,
                                           @Valid @RequestBody MenuSaveReq req) throws Exception {
        return ApiResponse.success(menuService.create(req, IdCodec.decode(userId)));
    }

    @Operation(summary = "管理员更新菜单")
    @PutMapping("/{id}")
    public ApiResponse<MenuAdminVO> update(@CurrentUserId String userId, @PathVariable String id,
                                           @Valid @RequestBody MenuSaveReq req) throws Exception {
        return ApiResponse.success(menuService.update(IdCodec.decode(id), req, IdCodec.decode(userId)));
    }

    @Operation(summary = "管理员设置菜单展示端")
    @PutMapping("/{id}/visible-clients")
    public ApiResponse<MenuAdminVO> updateVisibleClients(@CurrentUserId String userId, @PathVariable String id,
                                                         @Valid @RequestBody MenuVisibilityUpdateReq body) {
        return ApiResponse.success(menuService.updateVisibleClients(
                IdCodec.decode(id), body.getVisibleClients(), IdCodec.decode(userId)));
    }

    @Operation(summary = "管理员调整菜单排序")
    @PutMapping("/{id}/sort")
    public ApiResponse<MenuAdminVO> updateSort(@CurrentUserId String userId, @PathVariable String id,
                                               @Valid @RequestBody MenuSortUpdateReq body) throws Exception {
        return ApiResponse.success(menuService.updateSort(IdCodec.decode(id), body.getSortOrder(), IdCodec.decode(userId)));
    }

    @Operation(summary = "管理员删除菜单")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@CurrentUserId String userId, @PathVariable String id) {
        menuService.delete(IdCodec.decode(id), IdCodec.decode(userId));
        return ApiResponse.success();
    }
}
