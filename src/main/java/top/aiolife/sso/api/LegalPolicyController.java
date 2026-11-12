package top.aiolife.sso.api;

import io.swagger.v3.oas.annotations.Operation;

import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.sso.service.LegalPolicyService;

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
