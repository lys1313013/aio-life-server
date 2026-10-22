package top.aiolife.record.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import top.aiolife.core.util.LogSafeException;
import top.aiolife.record.mapper.IMbtiResultMapper;
import top.aiolife.record.pojo.entity.MbtiResultEntity;
import top.aiolife.record.service.IMbtiResultService;
import top.aiolife.system.service.IntegrationSystemConfigService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * MBTI测试结果服务实现
 *
 * @author Lys
 * @date 2026-03-23
 */
@Slf4j
@Service
public class MbtiResultServiceImpl extends ServiceImpl<IMbtiResultMapper, MbtiResultEntity> implements IMbtiResultService {
    
    private final RestTemplate restTemplate;
    private final IntegrationSystemConfigService configuration;

    public MbtiResultServiceImpl(RestTemplate restTemplate, IntegrationSystemConfigService configuration) {
        this.restTemplate = restTemplate;
        this.configuration = configuration;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map<String, Object> createTest() {
        try {
            // 文档地址：https://devil.ai/api
            var settings = configuration.mbti();
            String mbtiApiKey = settings.apiKey();
            String mbtiBaseUrl = settings.baseUrl();
            Map<String, Object> result = new HashMap<>();
            if (mbtiApiKey == null || mbtiApiKey.isBlank()) {
                result.put("success", false);
                result.put("message", "请管理员在系统配置中填写 MBTI API Key");
                return result;
            }

            var url = UriComponentsBuilder.fromUriString(mbtiBaseUrl).path("/v1/new_test")
                    .queryParam("api_key", "{key}").queryParam("lang", "cn").encode().buildAndExpand(mbtiApiKey).toUri();
            log.info("创建MBTI测试请求: {}/v1/new_test?lang=cn", mbtiBaseUrl);
            
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            log.info("创建MBTI测试响应: {}", response);
            
            if (response != null && response.containsKey("data")) {
                Map<String, Object> data = (Map<String, Object>) response.get("data");
                if (data != null && data.containsKey("test_id")) {
                    result.put("testId", data.get("test_id"));
                    result.put("testUrl", data.get("test_url"));
                    result.put("success", true);
                } else {
                    result.put("success", false);
                    result.put("message", "创建测试失败: 未获取到test_id");
                }
            } else if (response != null && response.containsKey("meta") && response.get("meta") instanceof Map<?, ?> meta) {
                Object statusCode = meta.get("status_code");
                Object metaSuccess = meta.get("success");
                Object metaMessage = meta.get("message");
                Object metaError = meta.get("error");
                String detail = String.valueOf(Objects.requireNonNullElse(metaMessage, metaError));
                if (detail.isBlank()) {
                    detail = "响应格式错误";
                }
                result.put("success", false);
                result.put("message", "创建测试失败: " + String.valueOf(statusCode) + " " + detail + " (success=" + String.valueOf(metaSuccess) + ")");
            } else {
                result.put("success", false);
                String detail = String.valueOf(response);
                if (detail.length() > 500) {
                    detail = detail.substring(0, 500) + "...";
                }
                result.put("message", "创建测试失败: 响应格式错误: " + detail);
            }
            
            return result;
        } catch (Exception e) {
            log.error("创建MBTI测试失败", LogSafeException.withoutMessages(e));
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "创建测试失败，请稍后重试或联系管理员检查接口配置");
            return result;
        }
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public Map<String, Object> checkTest(String testId) {
        try {
            var settings = configuration.mbti();
            String mbtiApiKey = settings.apiKey();
            String mbtiBaseUrl = settings.baseUrl();
            Map<String, Object> result = new HashMap<>();
            if (mbtiApiKey == null || mbtiApiKey.isBlank()) {
                result.put("success", false);
                result.put("message", "请管理员在系统配置中填写 MBTI API Key");
                return result;
            }

            var url = UriComponentsBuilder.fromUriString(mbtiBaseUrl).path("/v1/check_test")
                    .queryParam("api_key", "{key}").queryParam("test_id", "{id}").encode().buildAndExpand(mbtiApiKey, testId).toUri();
            log.info("查询MBTI测试结果请求: {}/v1/check_test?test_id={}", mbtiBaseUrl, testId);
            
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            log.info("查询MBTI测试结果响应: {}", response);
            
            if (response != null && response.containsKey("data")) {
                Map<String, Object> data = (Map<String, Object>) response.get("data");
                result.put("success", true);
                result.put("data", data);
                
                if (data != null && data.containsKey("prediction")) {
                    result.put("mbtiType", data.get("prediction"));
                    result.put("predictions", data.get("predictions"));
                    result.put("traitOrderConscious", data.get("trait_order_conscious"));
                    result.put("traitOrderShadow", data.get("trait_order_shadow"));
                    result.put("matches", data.get("matches"));
                    result.put("resultsPage", data.get("results_page"));
                }
            } else if (response != null && response.containsKey("meta") && response.get("meta") instanceof Map<?, ?> meta) {
                Object statusCode = meta.get("status_code");
                Object metaSuccess = meta.get("success");
                Object metaMessage = meta.get("message");
                Object metaError = meta.get("error");
                String detail = String.valueOf(Objects.requireNonNullElse(metaMessage, metaError));
                if (detail.isBlank()) {
                    detail = "响应格式错误";
                }
                result.put("success", false);
                result.put("message", "查询结果失败: " + String.valueOf(statusCode) + " " + detail + " (success=" + String.valueOf(metaSuccess) + ")");
            } else {
                result.put("success", false);
                String detail = String.valueOf(response);
                if (detail.length() > 500) {
                    detail = detail.substring(0, 500) + "...";
                }
                result.put("message", "查询结果失败: 响应格式错误: " + detail);
            }
            
            return result;
        } catch (Exception e) {
            log.error("查询MBTI测试结果失败", LogSafeException.withoutMessages(e));
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "查询结果失败，请稍后重试或联系管理员检查接口配置");
            return result;
        }
    }
    
    @Override
    public void saveResult(MbtiResultEntity result) {
        // 同一 testId 幂等保存，避免前端自动保存与手动保存重复插入
        LambdaQueryWrapper<MbtiResultEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(MbtiResultEntity::getUserId, result.getUserId());
        queryWrapper.eq(MbtiResultEntity::getTestId, result.getTestId());
        MbtiResultEntity existing = this.getOne(queryWrapper);
        if (existing != null) {
            result.setId(existing.getId());
            this.updateById(result);
            log.info("更新MBTI测试结果: userId={}, testId={}", result.getUserId(), result.getTestId());
            return;
        }
        this.save(result);
        log.info("保存MBTI测试结果: userId={}, mbtiType={}", result.getUserId(), result.getMbtiType());
    }
    
    @Override
    public List<MbtiResultEntity> getUserHistory(Long userId) {
        LambdaQueryWrapper<MbtiResultEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(MbtiResultEntity::getUserId, userId);
        queryWrapper.orderByDesc(MbtiResultEntity::getCreateTime);
        return this.list(queryWrapper);
    }

    @Override
    public boolean deleteResult(Long id, Long userId) {
        MbtiResultEntity entity = this.getById(id);
        if (entity == null || entity.getUserId() == null || !entity.getUserId().equals(userId)) {
            return false;
        }
        return this.removeById(id);
    }
}
