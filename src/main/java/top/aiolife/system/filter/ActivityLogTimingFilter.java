package top.aiolife.system.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** 仅保存时间，不读取或缓存请求正文。最终是否记录由采集切面决定。 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class ActivityLogTimingFilter extends OncePerRequestFilter {
    public static final String START_TIME = ActivityLogTimingFilter.class.getName() + ".time";
    public static final String START_NANOS = ActivityLogTimingFilter.class.getName() + ".nanos";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        request.setAttribute(START_NANOS, System.nanoTime());
        request.setAttribute(START_TIME, LocalDateTime.now(ZoneId.of("Asia/Shanghai")).truncatedTo(ChronoUnit.MILLIS));
        chain.doFilter(request, response);
    }
}
