package top.aiolife.record.client;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** 允许转备用供应商的受控失败，不携带上游响应或凭据。 */
public class MapFallbackException extends ResponseStatusException {
    public MapFallbackException(String reason) {
        super(HttpStatus.SERVICE_UNAVAILABLE, reason);
    }
}
