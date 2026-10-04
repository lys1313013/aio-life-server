package top.aiolife.sso.wechat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("test")
@SpringBootTest(classes = WechatTicketStoreIntegrationTest.Application.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class WechatTicketStoreIntegrationTest {
    @Autowired WechatTicketStore store;

    @Test void 同一个票据并发只能消费一次() throws Exception {
        var ticket = new WechatTicketStore.Ticket("test-openid", null, null, null);
        String value = store.issue(ticket);
        try (var executor = Executors.newFixedThreadPool(8)) {
            var calls = IntStream.range(0, 8).<Callable<Boolean>>mapToObj(i -> () -> {
                try { return store.consume(value).equals(ticket); }
                catch (ResponseStatusException e) { return false; }
            }).toList();
            int successes = 0;
            for (var result : executor.invokeAll(calls)) if (result.get()) successes++;
            assertEquals(1, successes);
        }
    }

    @Test void 伪造或未知票据不能通过() {
        assertThrows(ResponseStatusException.class, () -> store.consume("client-openid"));
        assertThrows(ResponseStatusException.class, () -> store.consume("a".repeat(64)));
    }

    @Test void 超过频率限制被拒绝() {
        String ip = UUID.randomUUID().toString();
        store.rateLimit("test", ip, 2);
        store.rateLimit("test", ip, 2);
        assertThrows(ResponseStatusException.class, () -> store.rateLimit("test", ip, 2));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({WechatTicketStore.class, WechatMiniProperties.class, ObjectMapper.class})
    static class Application {}
}
