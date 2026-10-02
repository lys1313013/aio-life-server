package top.aiolife.system.service;

import com.sun.net.httpserver.HttpServer;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import java.io.ByteArrayInputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import okhttp3.Headers;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.server.ResponseStatusException;
import top.aiolife.config.JsonConfig;
import top.aiolife.config.MinioConfig;
import top.aiolife.system.pojo.query.StorageObjectQuery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StorageAdminServiceTest {
    private MinioConfig config() {
        var config = new MinioConfig();
        config.setEndpoint("http://localhost:1300");
        config.setBucketName("business");
        config.setAccessKey("test-access");
        config.setSecretKey("test-secret");
        return config;
    }

    @Test
    void 原生分页只取一页且保留游标和特殊字符() throws Exception {
        var requests = new ArrayList<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String query = exchange.getRequestURI().getRawQuery();
            requests.add(query);
            String xml = query.contains("location")
                    ? "<LocationConstraint>us-east-1</LocationConstraint>"
                    : """
                    <ListBucketResult xmlns="http://s3.amazonaws.com/doc/2006-03-01/">
                      <Name>business</Name><EncodingType>url</EncodingType><Prefix></Prefix>
                      <MaxKeys>2</MaxKeys><IsTruncated>true</IsTruncated><NextContinuationToken>opaque+/=</NextContinuationToken>
                      <CommonPrefixes><Prefix>%E4%B8%AD%E6%96%87%20%2B%23%2F</Prefix></CommonPrefixes>
                      <Contents><Key>a%20%2B%23.jpg</Key><LastModified>2026-10-02T00:00:00.000Z</LastModified><ETag>test</ETag><Size>123</Size><StorageClass>STANDARD</StorageClass></Contents>
                    </ListBucketResult>
                    """;
            byte[] bytes = xml.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/xml");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        var config = config();
        config.setEndpoint("http://127.0.0.1:" + server.getAddress().getPort());
        try (var listClient = new StorageListClient(config)) {
            var service = new StorageAdminService(config, listClient, mock(MinioClient.class), mock(StorageFileReferenceGuard.class));
            var query = new StorageObjectQuery();
            query.setPageSize(2);
            var page = service.list(query);
            assertEquals("business", page.bucket());
            assertEquals("中文 +#/", page.items().get(0).key());
            assertTrue(page.items().get(0).directory());
            assertEquals("a +#.jpg", page.items().get(1).key());
            assertTrue(page.items().get(1).previewable());
            assertEquals("opaque+/=", page.nextCursor());
            assertEquals(1, requests.stream().filter(q -> q.contains("list-type")).count());
            query.setCursor(page.nextCursor());
            query.setPrefix("中文 +#/");
            service.list(query);
            String second = requests.getLast();
            assertTrue(second.contains("continuation-token=opaque%2B%2F%3D"));
            assertTrue(second.contains("max-keys=2"));
            assertTrue(second.contains("delimiter=%2F"));
            assertTrue(second.contains("prefix=%E4%B8%AD%E6%96%87%20%2B%23%2F"));
            var json = new JsonConfig().jacksonObjectMapper(new Jackson2ObjectMapperBuilder());
            assertEquals("123", json.readTree(json.writeValueAsString(page)).path("items").get(1).path("size").asText());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void 图片代理精确读取对象并禁止缓存() throws Exception {
        var client = mock(MinioClient.class);
        var stat = mock(StatObjectResponse.class);
        when(stat.size()).thenReturn(3L);
        when(client.statObject(any(StatObjectArgs.class))).thenReturn(stat);
        when(client.getObject(any(GetObjectArgs.class))).thenReturn(new GetObjectResponse(
                new Headers.Builder().build(), "business", "us-east-1", "中文 +#.JPG",
                new ByteArrayInputStream(new byte[]{1, 2, 3})));
        var service = new StorageAdminService(config(), mock(StorageListClient.class), client, mock(StorageFileReferenceGuard.class));
        var response = new MockHttpServletResponse();
        service.read("中文 +#.JPG", false, response);
        assertEquals("image/jpeg", response.getContentType());
        assertEquals("no-store", response.getHeader("Cache-Control"));
        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
        assertArrayEquals(new byte[]{1, 2, 3}, response.getContentAsByteArray());
        var args = org.mockito.ArgumentCaptor.forClass(GetObjectArgs.class);
        verify(client).getObject(args.capture());
        assertEquals("中文 +#.JPG", args.getValue().object());
        assertEquals("business", args.getValue().bucket());
    }

    @Test
    void 不内联主动内容或超大图片且允许作为附件下载() throws Exception {
        var client = mock(MinioClient.class);
        var stat = mock(StatObjectResponse.class);
        when(stat.size()).thenReturn(21 * 1024 * 1024L);
        when(client.statObject(any(StatObjectArgs.class))).thenReturn(stat);
        var service = new StorageAdminService(config(), mock(StorageListClient.class), client, mock(StorageFileReferenceGuard.class));
        for (String key : List.of("unsafe.svg", "unsafe.html", "large.jpg")) {
            var error = assertThrows(ResponseStatusException.class,
                    () -> service.read(key, false, new MockHttpServletResponse()));
            assertEquals(415, error.getStatusCode().value());
        }
        verify(client, never()).getObject(any());
        when(client.getObject(any(GetObjectArgs.class))).thenReturn(new GetObjectResponse(
                new Headers.Builder().build(), "business", "us-east-1", "unsafe.svg",
                new ByteArrayInputStream(new byte[0])));
        var response = new MockHttpServletResponse();
        service.read("unsafe.svg", true, response);
        assertEquals("application/octet-stream", response.getContentType());
        assertTrue(response.getHeader("Content-Disposition").startsWith("attachment;"));
    }

    @Test
    void 未配置桶时不连接存储() {
        var config = config();
        config.setBucketName("");
        var listClient = mock(StorageListClient.class);
        var service = new StorageAdminService(config, listClient, mock(MinioClient.class), mock(StorageFileReferenceGuard.class));
        assertThrows(ResponseStatusException.class, () -> service.list(new StorageObjectQuery()));
        verifyNoInteractions(listClient);
    }
}
