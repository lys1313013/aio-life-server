package top.aiolife.core.query;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Conventions;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.util.Assert;
import org.springframework.validation.annotation.ValidationAnnotationUtils;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 普通 ModelAttribute 无法还原 CommonQuery<T> 的泛型条件，且不使用 JsonFormat/JsonCreator。
 * 显式标注的查询 DTO 因此复用应用 ObjectMapper；不读取 GET 请求体。
 * 标注 Valid / Validated 时，绑定后使用 MVC 的 Validator 执行字段与分组校验。
 */
@RequiredArgsConstructor
public class QueryParamsArgumentResolver implements HandlerMethodArgumentResolver {

    private final ObjectMapper objectMapper;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(QueryParams.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                                  NativeWebRequest request, WebDataBinderFactory binderFactory) throws Exception {
        Map<String, Object> values = new LinkedHashMap<>();
        for (Map.Entry<String, String[]> entry : request.getParameterMap().entrySet()) {
            String name = entry.getKey();
            String[] items = entry.getValue();
            if (items.length > 0) {
                values.put(name, items.length == 1 ? items[0] : items);
            }
        }
        JavaType type = objectMapper.constructType(parameter.getGenericParameterType());
        if (CommonQuery.class.isAssignableFrom(parameter.getParameterType())) {
            Map<String, Object> pagination = new LinkedHashMap<>();
            for (String field : new String[]{"page", "pageSize"}) {
                if (values.containsKey(field)) {
                    pagination.put(field, values.remove(field));
                }
            }
            pagination.put("condition", values);
            values = pagination;
        }
        Object argument;
        try {
            JsonNode tree = objectMapper.valueToTree(values);
            argument = objectMapper.readerFor(type)
                    .with(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
                    .readValue(tree);
        } catch (IOException | IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "查询参数格式错误", exception);
        }
        for (Annotation annotation : parameter.getParameterAnnotations()) {
            Object[] hints = ValidationAnnotationUtils.determineValidationHints(annotation);
            if (hints == null) continue;
            Assert.state(binderFactory != null, "WebDataBinderFactory is required for query validation");
            WebDataBinder binder = binderFactory.createBinder(
                    request, argument, Conventions.getVariableNameForParameter(parameter));
            binder.validate(hints);
            if (binder.getBindingResult().hasErrors()) {
                String message = binder.getBindingResult().getAllErrors().getFirst().getDefaultMessage();
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        message == null ? "参数校验失败" : message);
            }
            break;
        }
        return argument;
    }
}
