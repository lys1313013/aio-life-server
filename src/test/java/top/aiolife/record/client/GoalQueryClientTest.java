package top.aiolife.record.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import top.aiolife.core.exception.SecondaryLockRequiredException;
import top.aiolife.record.config.GoalProgressProperties;
import static org.junit.jupiter.api.Assertions.*;

class GoalQueryClientTest {
    @Test void 查询二级锁保留正常解锁路径且不接受外部地址() throws Exception {
        for (String path : new String[]{"/finance/income", "https://other.invalid/"}) {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/graphql", exchange -> {
                byte[] body = ("{\"errors\":[{\"extensions\":{\"code\":\"SECONDARY_LOCK_REQUIRED\",\"menuPath\":\""
                        + path + "\"}}]}").getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(403, body.length);
                try (var out = exchange.getResponseBody()) { out.write(body); }
            });
            server.start();
            try {
                var properties = new GoalProgressProperties();
                properties.setQueryUrl(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/graphql"));
                var json = new ObjectMapper();
                var client = new GoalQueryClient(properties, json);
                if (path.startsWith("/")) {
                    var error = assertThrows(SecondaryLockRequiredException.class,
                            () -> client.execute("{progress}", json.createObjectNode(), "fixture-session"));
                    assertEquals(path, error.getMenuPath());
                } else {
                    assertThrows(IllegalArgumentException.class,
                            () -> client.execute("{progress}", json.createObjectNode(), "fixture-session"));
                }
            } finally { server.stop(0); }
        }
    }
}
