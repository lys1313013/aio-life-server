package top.aiolife.membership.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import cn.dev33.satoken.annotation.SaCheckRole;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.aiolife.core.id.IdCodec;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.web.CurrentUserId;
import top.aiolife.membership.pojo.req.MembershipProviderReq;
import top.aiolife.membership.pojo.vo.MembershipProviderVO;
import top.aiolife.membership.service.MembershipProviderService;

/**
 * 会员平台管理控制器。
 *
 * @author Lys
 * @date 2026/11/13
 */
@Tag(name = "会员平台管理")
@RestController
@RequiredArgsConstructor
@RequestMapping("/system/membership-providers")
@SaCheckRole("admin")
public class MembershipProviderAdminController {
    private final MembershipProviderService service;

    @Operation(summary = "管理员查询会员平台")
    @GetMapping
    public ApiResponse<List<MembershipProviderVO>> list() {
        return ApiResponse.success(service.list(false));
    }

    @Operation(summary = "管理员新增会员平台")
    @PostMapping
    public ApiResponse<MembershipProviderVO> create(@CurrentUserId String userId,
                                                    @Valid @RequestBody MembershipProviderReq req) {
        return ApiResponse.success(service.save(IdCodec.decode(userId), null, req));
    }

    @Operation(summary = "管理员更新会员平台")
    @PutMapping("/{id}")
    public ApiResponse<MembershipProviderVO> update(@CurrentUserId String userId, @PathVariable String id,
                                                    @Valid @RequestBody MembershipProviderReq req) {
        return ApiResponse.success(service.save(IdCodec.decode(userId), IdCodec.decode(id), req));
    }

    @Operation(summary = "管理员删除会员平台")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@CurrentUserId String userId, @PathVariable String id) {
        service.delete(IdCodec.decode(userId), IdCodec.decode(id));
        return ApiResponse.success();
    }
}
