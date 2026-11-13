package top.aiolife.config;

import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import top.aiolife.core.web.CurrentTokenArgumentResolver;
import top.aiolife.core.web.CurrentUserIdArgumentResolver;

@Configuration
public class CurrentUserConfig implements WebMvcConfigurer {

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentUserIdArgumentResolver());
        resolvers.add(new CurrentTokenArgumentResolver());
    }
}
