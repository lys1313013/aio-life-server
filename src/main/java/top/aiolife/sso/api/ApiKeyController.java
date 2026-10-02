package top.aiolife.sso.api;

import cn.dev33.satoken.stp.StpUtil;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.sso.convertor.ApiKeyConvertor;
import top.aiolife.sso.convertor.SsoApiConvertor;
import top.aiolife.sso.pojo.entity.ApiKeyEntity;
import top.aiolife.sso.pojo.req.ApiKeyGenerateReq;
import top.aiolife.sso.pojo.vo.ApiKeyCreatedVO;
import top.aiolife.sso.pojo.vo.ApiKeyVO;
import top.aiolife.sso.service.IApiKeyService;

/**
 * API Key 管理控制器
 *
 * @author Lys
 * @date 2026/03/09
 */
@RestController
@RequestMapping("/api-key")
@RequiredArgsConstructor
public class ApiKeyController {

    private final IApiKeyService apiKeyService;

    /**
     * 获取当前用户的所有 API Key (脱敏展示)
     */
    @GetMapping("/list")
    public ApiResponse<List<ApiKeyVO>> list() {
        long userId = StpUtil.getLoginIdAsLong();
        List<ApiKeyEntity> entities = apiKeyService.listByUserId(userId);
        return ApiResponse.success(ApiKeyConvertor.INSTANCE.entityList2VOList(entities));
    }

    /**
     * 生成新的 API Key
     */
    @PostMapping("/generate")
    public ApiResponse<ApiKeyCreatedVO> generate(@RequestBody ApiKeyGenerateReq req) {
        long userId = StpUtil.getLoginIdAsLong();
        return ApiResponse.success(SsoApiConvertor.INSTANCE.toApiKeyCreatedVO(apiKeyService.generateApiKey(userId, req.getRemark(), req.getExpireDays())));
    }

    /**
     * 删除 API Key
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        long userId = StpUtil.getLoginIdAsLong();
        ApiKeyEntity entity = apiKeyService.getById(id);
        if (entity != null && entity.getUserId().equals(userId)) {
            apiKeyService.removeById(id);
        }
        return ApiResponse.success();
    }
}
