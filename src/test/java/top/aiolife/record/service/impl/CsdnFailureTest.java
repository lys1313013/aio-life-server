package top.aiolife.record.service.impl;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CsdnFailureTest {
    @Test
    void 抓取失败不能伪装为零统计或空文章() throws IOException {
        var connection = mock(Connection.class);
        when(connection.timeout(10000)).thenReturn(connection);
        when(connection.userAgent(anyString())).thenReturn(connection);
        when(connection.get()).thenThrow(new IOException("fixture timeout"));
        try (var jsoup = mockStatic(Jsoup.class)) {
            jsoup.when(() -> Jsoup.connect("https://blog.csdn.net/fixture")).thenReturn(connection);
            var service = new CsdnServiceImpl();
            assertEquals("CSDN 统计请求失败，请稍后重试", assertThrows(IllegalStateException.class,
                    () -> service.getCsdnStats("fixture")).getMessage());
            assertEquals("CSDN 文章请求失败，请稍后重试", assertThrows(IllegalStateException.class,
                    () -> service.getCsdnArticles("fixture", 20)).getMessage());
        }
    }
}
