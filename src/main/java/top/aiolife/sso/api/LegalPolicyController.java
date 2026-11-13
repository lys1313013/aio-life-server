package top.aiolife.sso.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.sso.service.LegalPolicyService;

/**
 * 协议与隐私控制器
 *
 * @author Lys
 * @date 2026/11/13
 */
@Tag(name = "协议与隐私")
@RestController
@RequiredArgsConstructor
public class LegalPolicyController {
    public static final String PATH = "/auth/legal";
    private final LegalPolicyService policies;

    @Operation(summary = "查询当前用户协议和隐私政策")
    @GetMapping(PATH)
    public ApiResponse<LegalPolicyService.PublicPolicies> current(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return ApiResponse.success(policies.current());
    }
}
