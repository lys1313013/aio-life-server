package top.aiolife.core;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import top.aiolife.core.query.CommonQuery;

/** 将旧持久化测试数据转换为接口请求；系统字段不进入请求模型。 */
public final class ApiRequestFixtures {
    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private ApiRequestFixtures() {}
    public static <T> T request(Object source, Class<T> type) {
        return MAPPER.convertValue(source, type);
    }
    public static <T> List<T> requests(List<?> source, Class<T> type) {
        return source.stream().map(value -> request(value, type)).toList();
    }
    public static <T> CommonQuery<T> query(CommonQuery<?> source, Class<T> type) {
        return source.map(value -> request(value, type));
    }
}
