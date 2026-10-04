package top.aiolife.system.api;

import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.sso.mapper.UserMapper;
import top.aiolife.sso.pojo.entity.UserEntity;
import top.aiolife.system.pojo.vo.MenuRouteVO;
import top.aiolife.system.service.IMenuService;
import top.aiolife.system.service.MenuClient;

import java.util.List;

/**
 * 菜单控制器
 *
 * <p>用途：为前端提供后端菜单模式所需的路由树数据。</p>
 *
 * @author Ethan
 * @date 2026/04/19
 */
@RestController
@RequestMapping("/menu")
@RequiredArgsConstructor
public class MenuController {

    private final IMenuService menuService;

    private final UserMapper userMapper;

    /**
     * 获取当前用户可访问菜单路由树。
     *
     * <p>用途：前端 accessMode=backend 时拉取动态路由结构并生成侧边栏菜单。</p>
     *
     * @return 统一返回结构，data 为 RouteRecordStringComponent 结构的数组
     */
    @GetMapping("/all")
    public ApiResponse<List<MenuRouteVO>> all(@RequestParam(defaultValue = "web") String client) {
        long userId = StpUtil.getLoginIdAsLong();
        UserEntity user = userMapper.selectById(userId);
        List<String> roles = parseRoles(user == null ? null : user.getRole());
        return ApiResponse.success(menuService.getAccessibleMenuTree(roles, MenuClient.parse(client)));
    }

    public ApiResponse<List<MenuRouteVO>> all() { return all("web"); }

    private List<String> parseRoles(String roleStr) {
        if (!StringUtils.hasText(roleStr)) {
            return List.of("user");
        }
        return List.of(roleStr.split(",")).stream()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }
}

