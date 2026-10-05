package top.aiolife.record.weread;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.*;

class WereadReadCacheTest {
    @Test
    void read_并发合并且返回值修改不污染缓存() throws Exception {
        var cache = new WereadReadCache();
        var calls = new AtomicInteger();
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        Supplier<JsonNode> fetch = () -> {
            calls.incrementAndGet(); started.countDown();
            try { if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("timeout"); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("interrupted"); }
            return JsonNodeFactory.instance.objectNode().put("progress", 42);
        };
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> cache.read("user1:version1", "/book/getprogress", Map.of("bookId", "1"), fetch));
            assertTrue(started.await(5, TimeUnit.SECONDS));
            var second = workers.submit(() -> cache.read("user1:version1", "/book/getprogress", Map.of("bookId", "1"), fetch));
            release.countDown();
            ((ObjectNode) first.get(5, TimeUnit.SECONDS)).put("progress", 100);
            assertEquals(42, second.get(5, TimeUnit.SECONDS).path("progress").asInt());
            assertEquals(1, calls.get());
        } finally { release.countDown(); }
    }

    @Test
    void read_失败冷却三十秒后才重试且成功缓存五分钟() {
        var nanos = new AtomicLong();
        var calls = new AtomicInteger();
        var cache = new WereadReadCache(nanos::get);
        Supplier<JsonNode> fetch = () -> {
            if (calls.incrementAndGet() == 1) throw new IllegalStateException("暂时不可用");
            return JsonNodeFactory.instance.objectNode();
        };
        assertThrows(IllegalStateException.class, () -> cache.read("user1", "/shelf/sync", Map.of(), fetch));
        assertThrows(IllegalStateException.class, () -> cache.read("user1", "/shelf/sync", Map.of(), fetch));
        assertEquals(1, calls.get());
        nanos.set(Duration.ofSeconds(31).toNanos());
        cache.read("user1", "/shelf/sync", Map.of(), fetch);
        nanos.addAndGet(Duration.ofMinutes(4).toNanos());
        cache.read("user1", "/shelf/sync", Map.of(), fetch);
        assertEquals(2, calls.get());
        nanos.addAndGet(Duration.ofMinutes(2).toNanos());
        cache.read("user1", "/shelf/sync", Map.of(), fetch);
        assertEquals(3, calls.get());
    }
}
