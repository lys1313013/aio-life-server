package top.aiolife.sso.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.aiolife.core.id.IdCodec;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.web.CurrentUserId;
import top.aiolife.sso.convertor.ApiKeyConvertor;
import top.aiolife.sso.convertor.SsoApiConvertor;
import top.aiolife.sso.pojo.entity.ApiKeyEntity;
import top.aiolife.sso.pojo.req.ApiKeyGenerateReq;
import top.aiolife.sso.pojo.req.ApiKeyUpdateReq;
import top.aiolife.sso.pojo.vo.ApiKeyCreatedVO;
import top.aiolife.sso.pojo.vo.ApiKeyVO;
import top.aiolife.sso.service.IApiKeyService;

/**
 * API Key 管理控制器
 *
 * @author Lys
 * @date 2026/03/09
 */
@Tag(name = "密钥管理")
@RestController
@RequestMapping("/api-key")
@RequiredArgsConstructor
public class ApiKeyController {

    private final IApiKeyService apiKeyService;

    @Operation(summary = "查询我的 API Key")
    @GetMapping("/list")
    public ApiResponse<List<ApiKeyVO>> list(@CurrentUserId String userId) {
        List<ApiKeyEntity> entities = apiKeyService.listByUserId(IdCodec.decode(userId));
        return ApiResponse.success(ApiKeyConvertor.INSTANCE.entityList2VOList(entities));
    }

    @Operation(summary = "生成 API Key")
    @PostMapping("/generate")
    public ApiResponse<ApiKeyCreatedVO> generate(@CurrentUserId String userId,
                                                 @Valid @RequestBody ApiKeyGenerateReq req) {
        return ApiResponse.success(SsoApiConvertor.INSTANCE.toApiKeyCreatedVO(
                apiKeyService.generateApiKey(IdCodec.decode(userId), req.getRemark(), req.getExpireDays())));
    }

    @Operation(summary = "更新 API Key 备注")
    @PutMapping("/{id}")
    public ApiResponse<ApiKeyVO> update(@CurrentUserId String userId, @PathVariable String id,
                                        @Valid @RequestBody ApiKeyUpdateReq req) {
        return ApiResponse.success(ApiKeyConvertor.INSTANCE.entity2VO(
                apiKeyService.updateRemark(IdCodec.decode(userId), IdCodec.decode(id), req.getRemark())));
    }

    @Operation(summary = "删除 API Key")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@CurrentUserId String userId, @PathVariable String id) {
        apiKeyService.deleteOwned(IdCodec.decode(userId), IdCodec.decode(id));
        return ApiResponse.success();
    }
}
