package top.aiolife.system.api;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.sso.mapper.UserMapper;
import top.aiolife.sso.pojo.entity.UserEntity;
import top.aiolife.system.pojo.req.MenuSaveReq;
import top.aiolife.system.pojo.req.MenuSortUpdateReq;
import top.aiolife.system.pojo.req.MenuStatusUpdateReq;
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

    private final UserMapper userMapper;

    /**
     * 获取菜单树（管理端）。
     *
     * @return 统一返回结构，data 为菜单树
     */
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
    @GetMapping("/role-options")
    public ApiResponse<List<String>> roleOptions() {
        LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(UserEntity::getRole);
        wrapper.eq(UserEntity::getIsDeleted, 0);
        List<UserEntity> users = userMapper.selectList(wrapper);

        Set<String> roles = new TreeSet<>();
        roles.add("admin");
        roles.add("user");
        for (UserEntity u : users) {
            String roleStr = u == null ? null : u.getRole();
            if (roleStr == null || roleStr.isBlank()) {
                continue;
            }
            for (String r : roleStr.split(",")) {
                if (r == null) {
                    continue;
                }
                String v = r.trim();
                if (!v.isEmpty()) {
                    roles.add(v);
                }
            }
        }
        return ApiResponse.success(List.copyOf(roles));
    }

    /**
     * 新增菜单节点。
     *
     * @param req 菜单保存请求体
     * @return 统一返回结构，data 为新增后的菜单节点
     */
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
    @PutMapping("/{id}")
    public ApiResponse<MenuAdminVO> update(@PathVariable long id, @RequestBody MenuSaveReq req) throws Exception {
        long userId = StpUtil.getLoginIdAsLong();
        return ApiResponse.success(menuService.update(id, req, userId));
    }

    /**
     * 更新菜单启用状态。
     *
     * @param id 菜单ID
     * @param body 请求体，包含 status（0禁用，1启用）
     * @return 统一返回结构，data 为更新后的菜单节点
     */
    @PutMapping("/{id}/status")
    public ApiResponse<MenuAdminVO> updateStatus(@PathVariable long id, @Valid @RequestBody MenuStatusUpdateReq body) throws Exception {
        long userId = StpUtil.getLoginIdAsLong();
        return ApiResponse.success(menuService.updateStatus(id, body.getStatus(), userId));
    }

    /**
     * 更新菜单排序。
     *
     * @param id 菜单ID
     * @param body 请求体，包含 sort
     * @return 统一返回结构，data 为更新后的菜单节点
     */
    @PutMapping("/{id}/sort")
    public ApiResponse<MenuAdminVO> updateSort(@PathVariable long id, @Valid @RequestBody MenuSortUpdateReq body) throws Exception {
        long userId = StpUtil.getLoginIdAsLong();
        return ApiResponse.success(menuService.updateSort(id, body.getSort(), userId));
    }

    /**
     * 删除菜单节点。
     *
     * @param id 菜单ID
     * @return 统一返回结构
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable long id) {
        long userId = StpUtil.getLoginIdAsLong();
        menuService.delete(id, userId);
        return ApiResponse.success();
    }

}
