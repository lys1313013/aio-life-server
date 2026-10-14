package top.aiolife.record.service.impl;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.alibaba.fastjson2.JSONException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import top.aiolife.core.exception.ExceptionHandle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class GithubServiceImplTest {
    private final GithubServiceImpl service = new GithubServiceImpl();

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 429, 500, 503})
    void searchRecentCommits_上游失败不能返回成功空列表(int status) {
        HttpRequest request = mock(HttpRequest.class);
        when(request.header(anyString(), anyString())).thenReturn(request);
        HttpResponse response = mock(HttpResponse.class);
        try (MockedStatic<HttpRequest> http = mockStatic(HttpRequest.class)) {
            http.when(() -> HttpRequest.get(anyString())).thenReturn(request);
            when(request.execute()).thenReturn(response);
            when(response.getStatus()).thenReturn(status);
            when(response.isOk()).thenReturn(false);
            var error = assertThrows(IllegalStateException.class,
                    () -> service.searchRecentCommits("fixture", null, 1, 10));
            var apiResponse = new ExceptionHandle().handleException(error);
            assertNotEquals(0, apiResponse.getCode());
            assertEquals("GitHub 最近提交查询失败，请稍后重试", apiResponse.getMessage());
        }
    }

    @Test
    void searchRecentCommits_网络异常继续抛出() {
        HttpRequest request = mock(HttpRequest.class);
        when(request.header(anyString(), anyString())).thenReturn(request);
        try (MockedStatic<HttpRequest> http = mockStatic(HttpRequest.class)) {
            http.when(() -> HttpRequest.get(anyString())).thenReturn(request);
            when(request.execute()).thenThrow(new IllegalStateException("timeout"));
            assertThrows(IllegalStateException.class,
                    () -> service.searchRecentCommits("fixture", null, 1, 10));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "{\"items\":null}", "{\"items\":{}}", "not-json"})
    void searchRecentCommits_异常响应不能冒充空数据(String body) {
        HttpRequest request = mock(HttpRequest.class);
        when(request.header(anyString(), anyString())).thenReturn(request);
        HttpResponse response = mock(HttpResponse.class);
        try (MockedStatic<HttpRequest> http = mockStatic(HttpRequest.class)) {
            http.when(() -> HttpRequest.get(anyString())).thenReturn(request);
            when(request.execute()).thenReturn(response);
            when(response.isOk()).thenReturn(true);
            when(response.body()).thenReturn(body);
            var error = assertThrows(RuntimeException.class,
                    () -> service.searchRecentCommits("fixture", null, 1, 10));
            assertTrue(error instanceof IllegalStateException || error instanceof JSONException);
        }
    }

    @Test
    void searchRecentCommits_成功时区分空数据与提交记录() {
        HttpRequest request = mock(HttpRequest.class);
        when(request.header(anyString(), anyString())).thenReturn(request);
        HttpResponse response = mock(HttpResponse.class);
        try (MockedStatic<HttpRequest> http = mockStatic(HttpRequest.class)) {
            http.when(() -> HttpRequest.get(anyString())).thenReturn(request);
            when(request.execute()).thenReturn(response);
            when(response.isOk()).thenReturn(true);
            when(response.body()).thenReturn("{\"items\":[]}",
                    "{\"items\":[{\"sha\":\"abc\",\"commit\":{\"message\":\"修复\"}}]}");
            assertTrue(service.searchRecentCommits("fixture", null, 1, 10).isEmpty());
            var commits = service.searchRecentCommits("fixture", null, 1, 10);
            assertEquals(1, commits.size());
            assertEquals("abc", commits.getFirst().getId());
            assertEquals("修复", commits.getFirst().getMessage());
        }
    }
}
