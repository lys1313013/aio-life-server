package top.aiolife.record.api;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import top.aiolife.core.constant.ResponseCodeConst;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.record.pojo.entity.MbtiResultEntity;
import top.aiolife.record.service.IMbtiResultService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MBTI测试控制器
 *
 * @author Lys
 * @date 2026-03-23
 */
@Slf4j
@RestController
@RequestMapping("/mbti")
@RequiredArgsConstructor
public class MbtiController {

    private final IMbtiResultService mbtiResultService;
    private final ObjectMapper objectMapper;

    @PostMapping("/test")
    public ApiResponse<Map<String, Object>> createTest() {
        Map<String, Object> result = mbtiResultService.createTest();
        if (Boolean.TRUE.equals(result.get("success"))) {
            return ApiResponse.success(result);
        } else {
            String message = String.valueOf(result.getOrDefault("message", "创建测试失败"));
            return ApiResponse.error(ResponseCodeConst.RSCODE_COMMON_FAIL, message);
        }
    }

    @GetMapping("/test/{testId}")
    public ApiResponse<Map<String, Object>> checkTest(@PathVariable String testId) {
        Map<String, Object> result = mbtiResultService.checkTest(testId);
        return ApiResponse.success(result);
    }

    @PostMapping("/result")
    public ApiResponse<Void> saveResult(@RequestBody Map<String, Object> body) {
        long userId = StpUtil.getLoginIdAsLong();

        MbtiResultEntity entity = new MbtiResultEntity();
        entity.setUserId(userId);
        entity.setTestId((String) body.get("testId"));
        entity.setMbtiType((String) body.get("mbtiType"));
        entity.setResultsPage((String) body.get("resultsPage"));

        try {
            Map<String, Object> rawData = new HashMap<>();
            rawData.put("predictions", body.get("predictions"));
            rawData.put("traitOrderConscious", body.get("traitOrderConscious"));
            rawData.put("traitOrderShadow", body.get("traitOrderShadow"));
            rawData.put("matches", body.get("matches"));
            entity.setRawResult(objectMapper.writeValueAsString(rawData));
        } catch (Exception e) {
            log.error("序列化原始数据失败", e);
        }

        entity.fillCreateCommonField(userId);
        mbtiResultService.saveResult(entity);
        return ApiResponse.success();
    }

    @GetMapping("/results")
    public ApiResponse<List<MbtiResultEntity>> getHistory() {
        long userId = StpUtil.getLoginIdAsLong();
        List<MbtiResultEntity> history = mbtiResultService.getUserHistory(userId);
        return ApiResponse.success(history);
    }

    @GetMapping("/result/{id}")
    public ApiResponse<Map<String, Object>> getById(@PathVariable Long id) {
        long userId = StpUtil.getLoginIdAsLong();
        MbtiResultEntity result = mbtiResultService.getById(id);
        if (result == null || result.getUserId() == null || result.getUserId() != userId) {
            return ApiResponse.error(ResponseCodeConst.RSCODE_COMMON_FAIL, "记录不存在或无权限");
        }

        Map<String, Object> response = new HashMap<>();
        response.put("id", result.getId());
        response.put("testId", result.getTestId());
        response.put("mbtiType", result.getMbtiType());
        response.put("resultsPage", result.getResultsPage());
        response.put("createTime", result.getCreateTime());

        try {
            if (result.getRawResult() != null) {
                Map<String, Object> rawData = objectMapper.readValue(result.getRawResult(), Map.class);
                response.putAll(rawData);
            }
        } catch (Exception e) {
            log.error("解析原始数据失败", e);
        }

        return ApiResponse.success(response);
    }

    @DeleteMapping("/result/{id}")
    public ApiResponse<Void> deleteResult(@PathVariable Long id) {
        long userId = StpUtil.getLoginIdAsLong();
        boolean deleted = mbtiResultService.deleteResult(id, userId);
        if (!deleted) {
            return ApiResponse.error(ResponseCodeConst.RSCODE_COMMON_FAIL, "记录不存在或无权限");
        }
        return ApiResponse.success();
    }
}
