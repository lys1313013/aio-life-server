package top.aiolife.record.api;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import top.aiolife.config.CbtiConfig;
import top.aiolife.core.constant.ResponseCodeConst;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.record.pojo.entity.CbtiPersonalityEntity;
import top.aiolife.record.pojo.req.CbtiTestReq;
import top.aiolife.record.service.ICbtiService;

/**
 * CBTI 测试控制器
 *
 * <p>用途：提供 CBTI 题库、人格列表、结果计算与历史记录接口。</p>
 *
 * @author Ethan
 * @date 2026/04/18
 */
@RestController
@RequestMapping("/cbti")
@RequiredArgsConstructor
public class CbtiController {

    private final ICbtiService cbtiService;

    private final ObjectMapper objectMapper;

    private final CbtiConfig cbtiConfig;

    @Value("${aio.life.server.base-url}")
    private String serveBaseUrl;

    @Value("${aio.life.server.minio.bucket-name:aiolife}")
    private String minioBucketName;

    /**
     * 获取 CBTI 题库与维度定义接口。
     *
     * <p>用途：前端拉取题目、彩蛋题以及 15 维度定义（含每档解释文案）。</p>
     *
     * @return 统一返回结构，data 包含 questions/hiddenQuestions/dimensionDefs
     */
    @GetMapping("/questions")
    public ApiResponse<Map<String, Object>> questions() {
        try {
            return ApiResponse.success(cbtiService.getQuestions());
        } catch (Exception e) {
            return ApiResponse.error(ResponseCodeConst.RSCODE_COMMON_FAIL, "读取题库失败: " + e.getMessage());
        }
    }

    /**
     * 获取 CBTI 人格类型列表接口。
     *
     * <p>用途：前端展示“全部人格”与结果页人格卡片。</p>
     *
     * @return 统一返回结构，data 为人格列表
     */
    @GetMapping("/personalities")
    public ApiResponse<List<Map<String, Object>>> personalities() {
        List<CbtiPersonalityEntity> list = cbtiService.listPersonalities();
        List<Map<String, Object>> data = list.stream().map(this::toPersonalityView).toList();
        return ApiResponse.success(data);
    }

    /**
     * 获取 CBTI 人格类型详情接口。
     *
     * <p>用途：前端按人格 code 查看详情。</p>
     *
     * @param code 人格代码
     * @return 统一返回结构，data 为人格详情
     */
    @GetMapping("/personalities/{code}")
    public ApiResponse<Map<String, Object>> personality(@PathVariable String code) {
        CbtiPersonalityEntity entity = cbtiService.getPersonalityByCode(code);
        if (entity == null) {
            return ApiResponse.error(ResponseCodeConst.RSCODE_COMMON_FAIL, "人格不存在");
        }
        return ApiResponse.success(toPersonalityView(entity));
    }

    /**
     * CBTI 开始测试并保存历史接口。
     *
     * <p>用途：前端提交答题数据，后端计算匹配人格、返回结果，并写入测试历史表。</p>
     *
     * @param body 请求体，包含 answers（题号->选项值）与 hiddenAnswers（彩蛋答案）
     * @return 统一返回结构，data 为测试结果（人格、匹配度、维度结果、匹配排行等）
     */
    @PostMapping("/test")
    public ApiResponse<Map<String, Object>> test(@Valid @RequestBody CbtiTestReq body) throws Exception {
        long userId = StpUtil.getLoginIdAsLong();

        Map<Integer, Integer> answers = body.getAnswers();
        Map<String, Object> hiddenAnswers = body.getHiddenAnswers();

        Map<String, Object> result = cbtiService.testAndSave(userId, answers == null ? Map.of() : answers, hiddenAnswers);

        Object personality = result.get("personality");
        if (personality instanceof CbtiPersonalityEntity entity) {
            result.put("personality", toPersonalityView(entity));
        }

        return ApiResponse.success(result);
    }

    /**
     * 获取 CBTI 测试历史接口。
     *
     * <p>用途：前端展示用户历史测试结果列表。</p>
     *
     * @return 统一返回结构，data 为历史列表
     */
    @GetMapping("/results")
    public ApiResponse<List<Map<String, Object>>> results() {
        long userId = StpUtil.getLoginIdAsLong();
        List<Map<String, Object>> list = cbtiService.getUserHistory(userId);
        for (Map<String, Object> item : list) {
            Object obj = item.remove("imageObject");
            if (obj instanceof String objectName && StringUtils.hasText(objectName)) {
                item.put("imageUrl", buildPreviewUrl(objectName));
            }
        }
        return ApiResponse.success(list);
    }

    /**
     * 获取 CBTI 测试历史详情接口。
     *
     * <p>用途：前端查看单条历史结果详情并复盘（包含 answers/dimensions）。</p>
     *
     * @param id 历史记录ID
     * @return 统一返回结构，data 为历史详情
     */
    @GetMapping("/result/{id}")
    public ApiResponse<Map<String, Object>> resultDetail(@PathVariable Long id) {
        long userId = StpUtil.getLoginIdAsLong();
        Map<String, Object> detail = cbtiService.getHistoryDetail(id, userId);
        if (detail == null) {
            return ApiResponse.error(ResponseCodeConst.RSCODE_COMMON_FAIL, "记录不存在");
        }
        detail.remove("userId");
        Object personality = detail.get("personality");
        if (personality instanceof CbtiPersonalityEntity entity) {
            detail.put("personality", toPersonalityView(entity));
        }
        return ApiResponse.success(detail);
    }

    /**
     * 删除 CBTI 测试历史（逻辑删除）。
     *
     * @param id 历史记录ID
     * @return 是否删除成功
     */
    @DeleteMapping("/result/{id}")
    public ApiResponse<Boolean> deleteResult(@PathVariable Long id) {
        long userId = StpUtil.getLoginIdAsLong();
        boolean ok = cbtiService.deleteHistory(id, userId);
        if (!ok) {
            return ApiResponse.error(ResponseCodeConst.RSCODE_COMMON_FAIL, "记录不存在");
        }
        return ApiResponse.success(true);
    }

    private Map<String, Object> toPersonalityView(CbtiPersonalityEntity entity) {
        Map<String, Object> p = new HashMap<>();
        p.put("code", entity.getCode());
        p.put("name", entity.getName());
        p.put("motto", entity.getMotto());
        p.put("color", entity.getColor());
        p.put("description", entity.getDescription());
        p.put("techStack", entity.getTechStack());
        p.put("spirit", entity.getSpirit());
        p.put("isSpecial", Objects.equals(entity.getIsSpecial(), 1));
        if (StringUtils.hasText(entity.getImageObject())) {
            p.put("imageUrl", buildPreviewUrl(entity.getImageObject()));
        }
        p.put("vector", readJsonSafely(entity.getVector()));
        p.put("strengths", readJsonSafely(entity.getStrengths()));
        p.put("weaknesses", readJsonSafely(entity.getWeaknesses()));
        return p;
    }

    private Object readJsonSafely(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            return json;
        }
    }

    private String buildPreviewUrl(String objectName) {
        String normalized = objectName.startsWith("/") ? objectName.substring(1) : objectName;
        String bucketName = StringUtils.hasText(cbtiConfig.getBucketName()) ? cbtiConfig.getBucketName() : minioBucketName;
        return serveBaseUrl + "/file/preview/" + bucketName + "/" + normalized;
    }
}
