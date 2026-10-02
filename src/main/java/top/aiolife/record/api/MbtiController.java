package top.aiolife.record.api;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import top.aiolife.core.constant.ResponseCodeConst;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.record.convertor.RecordApiConvertor;
import top.aiolife.record.pojo.entity.MbtiResultEntity;
import top.aiolife.record.pojo.req.MbtiResultSaveReq;
import top.aiolife.record.pojo.vo.MbtiResultVO;
import top.aiolife.record.service.IMbtiResultService;

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
    public ApiResponse<Void> saveResult(@Valid @RequestBody MbtiResultSaveReq body) {
        long userId = StpUtil.getLoginIdAsLong();

        MbtiResultEntity entity = new MbtiResultEntity();
        entity.setUserId(userId);
        entity.setTestId(body.getTestId());
        entity.setMbtiType(body.getMbtiType());
        entity.setResultsPage(body.getResultsPage());

        try {
            Map<String, Object> rawData = new HashMap<>();
            rawData.put("predictions", body.getPredictions());
            rawData.put("traitOrderConscious", body.getTraitOrderConscious());
            rawData.put("traitOrderShadow", body.getTraitOrderShadow());
            rawData.put("matches", body.getMatches());
            entity.setRawResult(objectMapper.writeValueAsString(rawData));
        } catch (Exception e) {
            log.error("序列化原始数据失败", e);
        }

        entity.fillCreateCommonField(userId);
        mbtiResultService.saveResult(entity);
        return ApiResponse.success();
    }

    @GetMapping("/results")
    public ApiResponse<List<MbtiResultVO>> getHistory() {
        long userId = StpUtil.getLoginIdAsLong();
        List<MbtiResultEntity> history = mbtiResultService.getUserHistory(userId);
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toMbtiResultVOList(history));
    }

    @GetMapping("/result/{id}")
    public ApiResponse<top.aiolife.record.pojo.vo.MbtiResultDetailVO> getById(@PathVariable Long id) {
        long userId = StpUtil.getLoginIdAsLong();
        MbtiResultEntity result = mbtiResultService.getById(id);
        if (result == null || result.getUserId() == null || result.getUserId() != userId) {
            return ApiResponse.error(ResponseCodeConst.RSCODE_COMMON_FAIL, "记录不存在或无权限");
        }

        var response = new top.aiolife.record.pojo.vo.MbtiResultDetailVO();
        response.setId(result.getId());
        response.setTestId(result.getTestId());
        response.setMbtiType(result.getMbtiType());
        response.setResultsPage(result.getResultsPage());
        response.setCreateTime(result.getCreateTime());
        try {
            if (result.getRawResult() != null) {
                var rawData = objectMapper.readTree(result.getRawResult());
                response.setPredictions(rawData.get("predictions"));
                response.setTraitOrderConscious(rawData.get("traitOrderConscious"));
                response.setTraitOrderShadow(rawData.get("traitOrderShadow"));
                response.setMatches(rawData.get("matches"));
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
