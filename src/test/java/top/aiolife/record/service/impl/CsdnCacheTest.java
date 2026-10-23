package top.aiolife.record.service.impl;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class CsdnCacheTest {
    @Test
    void 统计和文章共享主页且账号与文章条数互不影响() throws IOException {
        var first = connection();
        var second = connection();
        when(first.get()).thenReturn(homepage(100));
        when(second.get()).thenReturn(homepage(200));
        var service = new CsdnServiceImpl();

        try (var jsoup = mockStatic(Jsoup.class)) {
            jsoup.when(() -> Jsoup.connect("https://blog.csdn.net/first")).thenReturn(first);
            jsoup.when(() -> Jsoup.connect("https://blog.csdn.net/second")).thenReturn(second);

            assertEquals(100, service.getCsdnStats("first").getViewCount());
            assertEquals(1, service.getCsdnArticles("first", 1).size());
            var articles = service.getCsdnArticles("first", 2);
            assertEquals(2, articles.size());
            assertEquals("文章一", articles.getFirst().getTitle());
            assertEquals("2", articles.get(1).getId());
            assertEquals(200, service.getCsdnStats("second").getViewCount());
            assertEquals(100, service.getCsdnStats("first").getViewCount());
            verify(first).get();
            verify(second).get();
        }
    }

    @Test
    void 三十秒后重新抓取且读取缓存不延长有效期() throws IOException {
        var nanos = new AtomicLong();
        var service = new CsdnServiceImpl(nanos::get);
        var connection = connection();
        when(connection.get()).thenReturn(homepage(100), homepage(200));

        withConnection(connection, () -> {
            assertEquals(100, service.getCsdnStats("fixture").getViewCount());
            nanos.set(Duration.ofSeconds(29).toNanos());
            assertEquals(100, service.getCsdnStats("fixture").getViewCount());
            nanos.set(Duration.ofSeconds(30).toNanos());
            assertEquals(200, service.getCsdnStats("fixture").getViewCount());
            assertEquals(2, service.getCsdnArticles("fixture", 20).size());
            return null;
        });
        verify(connection, times(2)).get();
    }

    @Test
    void 统计和文章并发只发起一次抓取() throws Exception {
        var service = new CsdnServiceImpl();
        var connection = connection();
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var fetching = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var document = homepage(100);
        when(connection.get()).thenAnswer(invocation -> {
            fetching.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return document;
        });

        try (var workers = Executors.newFixedThreadPool(2)) {
            var stats = workers.submit(() -> withConnection(connection, () -> {
                awaitStart(ready, start);
                return service.getCsdnStats("fixture");
            }));
            var articles = workers.submit(() -> withConnection(connection, () -> {
                awaitStart(ready, start);
                return service.getCsdnArticles("fixture", 20);
            }));
            try {
                assertTrue(ready.await(5, TimeUnit.SECONDS));
                start.countDown();
                assertTrue(fetching.await(5, TimeUnit.SECONDS));
            } finally {
                start.countDown();
                release.countDown();
            }
            assertEquals(100, stats.get(5, TimeUnit.SECONDS).getViewCount());
            assertEquals(2, articles.get(5, TimeUnit.SECONDS).size());
            verify(connection).get();
        }
    }

    @Test
    void 失败不缓存且下次成功后两个接口共享结果() throws IOException {
        var service = new CsdnServiceImpl();
        var connection = connection();
        var error = new IOException("fixture timeout");
        when(connection.get()).thenThrow(error).thenReturn(homepage(100));

        withConnection(connection, () -> {
            assertSame(error, assertThrows(IllegalStateException.class,
                    () -> service.getCsdnStats("fixture")).getCause());
            assertEquals(2, service.getCsdnArticles("fixture", 20).size());
            assertEquals(100, service.getCsdnStats("fixture").getViewCount());
            return null;
        });
        verify(connection, times(2)).get();
    }

    private static Connection connection() {
        var connection = mock(Connection.class);
        when(connection.timeout(10000)).thenReturn(connection);
        when(connection.userAgent(anyString())).thenReturn(connection);
        return connection;
    }

    private static <T> T withConnection(Connection connection, Supplier<T> action) {
        try (var jsoup = mockStatic(Jsoup.class)) {
            jsoup.when(() -> Jsoup.connect("https://blog.csdn.net/fixture")).thenReturn(connection);
            return action.get();
        }
    }

    private static void awaitStart(CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            assertTrue(start.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static Document homepage(int views) {
        return Jsoup.parse("""
                <span class="user-profile-statistics-num">%d</span>
                <span class="user-profile-statistics-name">总访问量</span>
                <article class="blog-list-box">
                    <a href="https://blog.csdn.net/fixture/article/details/1"><h4>文章一</h4></a>
                </article>
                <article class="blog-list-box">
                    <a href="https://blog.csdn.net/fixture/article/details/2"><h4>文章二</h4></a>
                </article>
                """.formatted(views));
    }
}
