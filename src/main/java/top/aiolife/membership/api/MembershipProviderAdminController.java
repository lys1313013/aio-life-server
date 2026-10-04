package top.aiolife.membership.api;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
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
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.membership.pojo.req.MembershipProviderReq;
import top.aiolife.membership.pojo.vo.MembershipProviderVO;
import top.aiolife.membership.service.MembershipProviderService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/system/membership-providers")
@SaCheckRole("admin")
public class MembershipProviderAdminController {
    private final MembershipProviderService service;

    @GetMapping
    public ApiResponse<List<MembershipProviderVO>> list() { return ApiResponse.success(service.list(false)); }
    @PostMapping
    public ApiResponse<MembershipProviderVO> create(@Valid @RequestBody MembershipProviderReq req) {
        return ApiResponse.success(service.save(StpUtil.getLoginIdAsLong(), null, req));
    }
    @PutMapping("/{id}")
    public ApiResponse<MembershipProviderVO> update(@PathVariable Long id, @Valid @RequestBody MembershipProviderReq req) {
        return ApiResponse.success(service.save(StpUtil.getLoginIdAsLong(), id, req));
    }
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(StpUtil.getLoginIdAsLong(), id);
        return ApiResponse.success();
    }
}
