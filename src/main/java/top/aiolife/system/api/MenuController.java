package top.aiolife.system.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.aiolife.core.id.IdCodec;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.web.CurrentUserId;
import top.aiolife.sso.service.IUserService;
import top.aiolife.system.pojo.vo.MenuRouteVO;
import top.aiolife.system.service.IMenuService;
import top.aiolife.system.service.MenuClient;
import top.aiolife.system.service.MenuVisualService;
import top.aiolife.system.pojo.vo.MenuVisualsVO;

import java.util.List;

/**
 * 菜单控制器
 *
 * <p>用途：为前端提供后端菜单模式所需的路由树数据。</p>
 *
 * @author Ethan
 * @date 2026/04/19
 */
@Tag(name = "菜单")
@RestController
@RequestMapping("/menu")
@RequiredArgsConstructor
public class MenuController {

    private final IMenuService menuService;

    private final IUserService userService;
    private final MenuVisualService visuals;

    @Operation(summary = "查询当前用户可访问菜单")
    @GetMapping("/all")
    public ApiResponse<List<MenuRouteVO>> all(
            @CurrentUserId String userId,
            @Parameter(description = "客户端：web、mobile") @RequestParam(defaultValue = "web") String client) {
        List<String> roles = userService.listRoles(IdCodec.decode(userId));
        return ApiResponse.success(menuService.getAccessibleMenuTree(roles, MenuClient.parse(client)));
    }

    @Operation(summary = "查询业务图标和颜色")
    @GetMapping("/visuals")
    public ApiResponse<MenuVisualsVO> visuals(
            @CurrentUserId String userId,
            @Parameter(description = "客户端：web、mobile") @RequestParam(defaultValue = "web") String client) {
        return ApiResponse.success(visuals.get(IdCodec.decode(userId), MenuClient.parse(client)));
    }
}
