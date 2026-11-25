package top.aiolife.record.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.aiolife.core.id.IdCodec;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.web.CurrentUserId;
import top.aiolife.record.convertor.RecordApiConvertor;
import top.aiolife.record.pojo.entity.UserThirdPartyAccountEntity;
import top.aiolife.record.pojo.req.UserThirdPartyAccountCreateReq;
import top.aiolife.record.pojo.req.UserThirdPartyAccountUpdateReq;
import top.aiolife.record.pojo.req.UserThirdPartyAccountVerifyReq;
import top.aiolife.record.pojo.vo.UserThirdPartyAccountVerifyVO;
import top.aiolife.record.pojo.vo.UserThirdPartyAccountVO;
import top.aiolife.record.service.IUserThirdPartyAccountService;

/**
 * 用户第三方账号绑定接口
 */
@Tag(name = "第三方账号绑定")
@RestController
@RequiredArgsConstructor
@RequestMapping("/userbinds")
public class UserThirdPartyAccountController {

    private final IUserThirdPartyAccountService userThirdPartyAccountService;

    /**
     * 获取当前用户的绑定列表
     */
    @Operation(summary = "查询第三方账号绑定")
    @GetMapping("/list")
    public ApiResponse<List<UserThirdPartyAccountVO>> list(@CurrentUserId String userId,
                                              @RequestParam(required = false) Boolean includeToken) {
        List<UserThirdPartyAccountEntity> list = userThirdPartyAccountService.listForUser(IdCodec.decode(userId), includeToken);
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toUserThirdPartyAccountVOList(list));
    }

    @Operation(summary = "验证第三方账号绑定", description = "查询表单里的账号或凭证是否可用，不保存绑定")
    @PostMapping("/verify")
    public ApiResponse<UserThirdPartyAccountVerifyVO> verify(@CurrentUserId String userId,
                                                           @Valid @RequestBody UserThirdPartyAccountVerifyReq request) {
        return ApiResponse.success(userThirdPartyAccountService.verifyForUser(IdCodec.decode(userId), request));
    }

    /**
     * 新增绑定
     */
    @Operation(summary = "新增第三方账号绑定")
    @PostMapping
    public ApiResponse<Boolean> add(@CurrentUserId String userId,
                                    @Valid @RequestBody UserThirdPartyAccountCreateReq userThirdPartyAccountEntityReq) {
        UserThirdPartyAccountEntity userThirdPartyAccountEntity = RecordApiConvertor.INSTANCE.fromUserThirdPartyAccountCreateReq(userThirdPartyAccountEntityReq);
        return ApiResponse.success(userThirdPartyAccountService.createForUser(IdCodec.decode(userId), userThirdPartyAccountEntity));
    }

    /**
     * 更新绑定
     */
    @Operation(summary = "更新第三方账号绑定")
    @PutMapping
    public ApiResponse<Boolean> update(@CurrentUserId String userId,
                                       @Valid @RequestBody UserThirdPartyAccountUpdateReq userThirdPartyAccountEntityReq) {
        UserThirdPartyAccountEntity userThirdPartyAccountEntity = RecordApiConvertor.INSTANCE.fromUserThirdPartyAccountUpdateReq(userThirdPartyAccountEntityReq);
        return ApiResponse.success(userThirdPartyAccountService.updateForUser(IdCodec.decode(userId), userThirdPartyAccountEntity));
    }

    /**
     * 删除绑定
     */
    @Operation(summary = "删除第三方账号绑定")
    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(@CurrentUserId String userId, @PathVariable String id) {
        return ApiResponse.success(userThirdPartyAccountService.deleteForUser(
                IdCodec.decode(userId), IdCodec.decode(id)));
    }
}
