package top.aiolife.sso.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.sso.pojo.req.CloseAccountReq;
import top.aiolife.sso.service.AccountClosureService;

@RestController
@RequiredArgsConstructor
public class AccountClosureController {
    private final AccountClosureService closure;

    @PostMapping("/auth/account/close")
    public ApiResponse<Void> close(@Valid @RequestBody CloseAccountReq request) {
        closure.close(AccountClosureService.requestUser(), request);
        return ApiResponse.success();
    }
}
