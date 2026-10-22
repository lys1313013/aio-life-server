package top.aiolife.mcp.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * MCP HTTP transport is for non-browser clients. Reject browser requests before global CORS handling.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class McpOriginFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"/mcp".equals(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (request.getHeader("Origin") != null) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        filterChain.doFilter(request, response);
    }
}
