package top.aiolife.record.client;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 微信读书 Agent API Gateway 客户端。
 */
@Slf4j
@Component
public class WeReadClient {

    private static final String GATEWAY_URL = "https://i.weread.qq.com/api/agent/gateway";
    private static final String SKILL_VERSION = "1.0.4";
    private static final int TIMEOUT_MILLIS = 10_000;

    /**
     * 查询当前自然月的阅读统计。
     *
     * @param apiKey 用户在微信读书获取的 API Key
     * @return 微信读书阅读统计数据
     */
    public JSONObject getCurrentMonthReadData(String apiKey) {
        String normalizedApiKey = apiKey == null ? "" : apiKey.trim();
        if (normalizedApiKey.isEmpty()) {
            throw new IllegalArgumentException("请先配置微信读书 API Key");
        }

        JSONObject requestBody = new JSONObject();
        requestBody.put("api_name", "/readdata/detail");
        requestBody.put("mode", "monthly");
        requestBody.put("baseTime", 0);
        requestBody.put("skill_version", SKILL_VERSION);

        try (HttpResponse response = HttpRequest.post(GATEWAY_URL)
                .header("Authorization", "Bearer " + normalizedApiKey)
                .header("Content-Type", "application/json")
                .timeout(TIMEOUT_MILLIS)
                .body(requestBody.toJSONString())
                .execute()) {
            if (response.getStatus() == 401 || response.getStatus() == 403) {
                throw new IllegalArgumentException("微信读书 API Key 无效或已过期");
            }
            if (!response.isOk()) {
                log.warn("微信读书接口请求失败，status={}", response.getStatus());
                throw new IllegalStateException("微信读书暂时无法访问，请稍后重试");
            }

            JSONObject result = JSON.parseObject(response.body());
            if (result == null) {
                throw new IllegalStateException("微信读书返回数据为空");
            }
            if (result.containsKey("errcode") && result.getIntValue("errcode") != 0) {
                log.warn("微信读书接口返回错误，errcode={}, errmsg={}",
                        result.getIntValue("errcode"), result.getString("errmsg"));
                throw new IllegalStateException("微信读书阅读数据暂时无法获取");
            }
            return result;
        }
    }
}
