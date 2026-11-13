package top.aiolife.sso.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import top.aiolife.core.id.IdCodec;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.web.CurrentUserId;
import top.aiolife.sso.pojo.req.CloseAccountReq;
import top.aiolife.sso.service.AccountClosureService;

/**
 * 账号注销控制器
 *
 * @author Lys
 * @date 2026/11/13
 */
@Tag(name = "账号注销")
@RestController
@RequiredArgsConstructor
public class AccountClosureController {
    private final AccountClosureService closure;

    @Operation(summary = "注销当前账号")
    @PostMapping("/auth/account/close")
    public ApiResponse<Void> close(@CurrentUserId String userId, @Valid @RequestBody CloseAccountReq request) {
        closure.close(IdCodec.decode(userId), request);
        return ApiResponse.success();
    }
}
