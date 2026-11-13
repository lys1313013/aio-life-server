package top.aiolife.system.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.aiolife.core.id.IdCodec;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.web.CurrentUserId;
import top.aiolife.sso.service.IUserService;
import top.aiolife.system.pojo.req.QuickNavSaveReq;
import top.aiolife.system.pojo.vo.QuickNavCandidateVO;
import top.aiolife.system.pojo.vo.QuickNavItemVO;
import top.aiolife.system.service.IQuickNavService;
import top.aiolife.system.service.MenuClient;

import java.util.List;

/**
 * 用户首页快捷导航控制器
 *
 * @author Ethan
 * @date 2026/06/05
 */
@Tag(name = "快捷导航")
@RestController
@RequestMapping("/quick-nav")
@RequiredArgsConstructor
public class QuickNavController {

    private final IQuickNavService quickNavService;

    private final IUserService userService;

    @Operation(summary = "查询快捷导航可选菜单")
    @GetMapping("/candidates")
    public ApiResponse<List<QuickNavCandidateVO>> candidates(
            @CurrentUserId String userId,
            @Parameter(description = "客户端：web、mobile") @RequestParam(defaultValue = "web") String client) {
        return ApiResponse.success(quickNavService.listCandidates(
                userService.listRoles(IdCodec.decode(userId)), MenuClient.parse(client)));
    }

    @Operation(summary = "查询我的快捷导航")
    @GetMapping("/my")
    public ApiResponse<List<QuickNavItemVO>> my(
            @CurrentUserId String userId,
            @Parameter(description = "客户端：web、mobile") @RequestParam(defaultValue = "web") String client) {
        return ApiResponse.success(quickNavService.listMy(
                IdCodec.decode(userId), userService.listRoles(IdCodec.decode(userId)), MenuClient.parse(client)));
    }

    @Operation(summary = "保存当前端快捷导航")
    @PostMapping("/my")
    public ApiResponse<List<QuickNavItemVO>> save(
            @CurrentUserId String userId,
            @Valid @RequestBody QuickNavSaveReq req,
            @Parameter(description = "客户端：web、mobile") @RequestParam(defaultValue = "web") String client) {
        return ApiResponse.success(quickNavService.saveMy(
                IdCodec.decode(userId), userService.listRoles(IdCodec.decode(userId)), req, MenuClient.parse(client)));
    }
}
