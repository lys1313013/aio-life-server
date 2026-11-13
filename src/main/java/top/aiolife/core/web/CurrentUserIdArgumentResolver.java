package top.aiolife.core.web;

import cn.dev33.satoken.stp.StpUtil;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * 解析 {@link CurrentUserId}：默认返回字符串，兼容迁移期的 long/Long 参数。
 */
public class CurrentUserIdArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        if (!parameter.hasParameterAnnotation(CurrentUserId.class)) {
            return false;
        }
        Class<?> type = parameter.getParameterType();
        return type == String.class || type == Long.class || type == long.class;
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                                  NativeWebRequest request, WebDataBinderFactory binderFactory) {
        if (parameter.getParameterType() == String.class) {
            return StpUtil.getLoginIdAsString();
        }
        return StpUtil.getLoginIdAsLong();
    }
}
