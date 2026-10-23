package top.aiolife.record.api;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.aiolife.record.client.TencentMapProxyClient;
import top.aiolife.record.filter.TencentMapProxySessionFilter;
import top.aiolife.sso.util.RequestLoginContext;

/** SDK 要求原始 JSON/JSONP，不能套业务响应；仍由全局拦截器执行完整登录校验。 */
@RestController
@RequiredArgsConstructor
public class TencentMapProxyController {
    private final TencentMapProxyClient client;

    @GetMapping({TencentMapProxySessionFilter.PATH, TencentMapProxySessionFilter.PATH + "/checkKey",
            TencentMapProxySessionFilter.PATH + "/oversea"})
    public ResponseEntity<byte[]> proxy(HttpServletRequest request, @RequestParam MultiValueMap<String, String> parameters) {
        String path = request.getRequestURI().substring(request.getContextPath().length()
                + TencentMapProxySessionFilter.PATH.length());
        return client.forward(RequestLoginContext.requireUserId(), path, parameters);
    }
}
