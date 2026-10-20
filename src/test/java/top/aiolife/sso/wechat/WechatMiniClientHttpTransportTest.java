package top.aiolife.sso.wechat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class WechatMiniClientHttpTransportTest {
    @Test
    void 微信JSON请求携带准确长度且不使用分块传输() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> contentLength = new AtomicReference<>();
        AtomicReference<String> transferEncoding = new AtomicReference<>();
        AtomicReference<byte[]> received = new AtomicReference<>();
        server.createContext("/cgi-bin/stable_token", exchange -> {
            try (exchange) {
                contentLength.set(exchange.getRequestHeaders().getFirst("Content-Length"));
                transferEncoding.set(exchange.getRequestHeaders().getFirst("Transfer-Encoding"));
                received.set(exchange.getRequestBody().readAllBytes());
                exchange.sendResponseHeaders(200, 0);
                exchange.getResponseBody().write("{}".getBytes(StandardCharsets.UTF_8));
            }
        });
        server.start();
        try {
            var body = Map.of("grant_type", "client_credential", "appid", "wx_test",
                    "secret", "test-secret", "force_refresh", false);
            WechatMiniClient.builder().baseUrl("http://127.0.0.1:" + server.getAddress().getPort()).build()
                    .post().uri("/cgi-bin/stable_token").contentType(MediaType.APPLICATION_JSON)
                    .body(body).retrieve().toBodilessEntity();
            assertNull(transferEncoding.get());
            assertEquals(String.valueOf(received.get().length), contentLength.get());
            ObjectMapper json = new ObjectMapper();
            assertEquals(json.valueToTree(body), json.readTree(received.get()));
        } finally {
            server.stop(0);
        }
    }
}
