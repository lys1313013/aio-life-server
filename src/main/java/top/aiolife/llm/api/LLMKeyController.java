package top.aiolife.llm.api;

import cn.dev33.satoken.stp.StpUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import top.aiolife.core.constant.ResponseCodeConst;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.llm.pojo.entity.LLMKeyEntity;
import top.aiolife.llm.service.LLMKeyService;

import java.util.List;

@Slf4j
@RestController
@AllArgsConstructor
@RequestMapping("/llm/key")
public class LLMKeyController {

    private final LLMKeyService llmKeyService;

    @PostMapping
    public ApiResponse<Void> saveLLMKey(@RequestBody LLMKeyEntity llmKeyEntity) {
        try {
            long userId = StpUtil.getLoginIdAsLong();
            llmKeyEntity.setUserId(userId);
            llmKeyService.saveLLMKey(llmKeyEntity);
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("Failed to save LLM key: {}", e.getMessage(), e);
            return ApiResponse.error(ResponseCodeConst.RSCODE_COMMON_FAIL, e.getMessage());
        }
    }

    @PutMapping
    public ApiResponse<Void> updateLLMKey(@RequestBody LLMKeyEntity llmKeyEntity) {
        try {
            long userId = StpUtil.getLoginIdAsLong();
            llmKeyEntity.setUserId(userId);
            llmKeyService.updateLLMKey(llmKeyEntity);
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("Failed to update LLM key: {}", e.getMessage(), e);
            return ApiResponse.error(ResponseCodeConst.RSCODE_COMMON_FAIL, e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteLLMKey(@PathVariable Long id) {
        try {
            long userId = StpUtil.getLoginIdAsLong();
            llmKeyService.deleteLLMKey(id, userId);
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("Failed to delete LLM key: {}", e.getMessage(), e);
            return ApiResponse.error(ResponseCodeConst.RSCODE_COMMON_FAIL, e.getMessage());
        }
    }

    @GetMapping("/list")
    public ApiResponse<List<LLMKeyEntity>> getLLMKeyList() {
        try {
            long userId = StpUtil.getLoginIdAsLong();
            List<LLMKeyEntity> list = llmKeyService.getLLMKeyList(userId);
            return ApiResponse.success(list);
        } catch (Exception e) {
            log.error("Failed to get LLM key list: {}", e.getMessage(), e);
            return ApiResponse.error(ResponseCodeConst.RSCODE_COMMON_FAIL, e.getMessage());
        }
    }

    @GetMapping("/default")
    public ApiResponse<LLMKeyEntity> getDefaultLLMKey() {
        try {
            long userId = StpUtil.getLoginIdAsLong();
            LLMKeyEntity llmKeyEntity = llmKeyService.getDefaultLLMKey(userId);
            return ApiResponse.success(llmKeyEntity);
        } catch (Exception e) {
            log.error("Failed to get default LLM key: {}", e.getMessage(), e);
            return ApiResponse.error(ResponseCodeConst.RSCODE_COMMON_FAIL, e.getMessage());
        }
    }

    @PutMapping("/default/{id}")
    public ApiResponse<Void> setDefaultLLMKey(@PathVariable Long id) {
        try {
            long userId = StpUtil.getLoginIdAsLong();
            llmKeyService.setDefaultLLMKey(id, userId);
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("Failed to set default LLM key: {}", e.getMessage(), e);
            return ApiResponse.error(ResponseCodeConst.RSCODE_COMMON_FAIL, e.getMessage());
        }
    }
}
