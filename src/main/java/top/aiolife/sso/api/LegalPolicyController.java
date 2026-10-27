package top.aiolife.sso.api;

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

    @GetMapping(PATH)
    public ApiResponse<LegalPolicyService.PublicPolicies> current(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return ApiResponse.success(policies.current());
    }
}
