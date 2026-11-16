package top.aiolife.core.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.*;
import static top.aiolife.support.ImageFixtures.image;

class FileContentPolicyTest {
    @ParameterizedTest
    @ValueSource(strings = {"lossless", "lossy"})
    void WebP真实解码并保留原始图片(String mode) throws Exception {
        try (var input = getClass().getResourceAsStream("/file-security/" + mode + ".webp")) {
            assertNotNull(input);
            byte[] bytes = input.readAllBytes();
            assertEquals("image/webp", FileContentPolicy.requireImage(bytes).contentType());
            var response = new MockHttpServletResponse();
            FileContentPolicy.writeResponse(new ByteArrayInputStream(bytes), response, "image.webp", false,
                    FileContentPolicy.requireImage(bytes).contentType());
            assertEquals("image/webp", response.getContentType());
            assertArrayEquals(bytes, response.getContentAsByteArray());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"png", "jpeg", "gif", "bmp"})
    void 真实栅格图片按内容识别并保留原始字节(String format) throws Exception {
        byte[] bytes = image(format);
        var response = new MockHttpServletResponse();
        FileContentPolicy.writeResponse(new ByteArrayInputStream(bytes), response, "fake.html", false,
                FileContentPolicy.requireImage(bytes).contentType());
        assertEquals("image/" + format, response.getContentType());
        assertTrue(response.getHeader("Content-Disposition").startsWith("inline;"));
        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
        assertEquals("default-src 'none'; sandbox", response.getHeader("Content-Security-Policy"));
        assertArrayEquals(bytes, response.getContentAsByteArray());
    }

    @ParameterizedTest
    @ValueSource(strings = {"<html><script>alert(1)</script></html>",
            "<svg xmlns='http://www.w3.org/2000/svg' onload='alert(1)'/>",
            "GIF89a<script>alert(1)</script>", "%PDF-1.7\nattachment", ""})
    void 活动内容或伪造魔数即使使用图片后缀也只能下载(String body) throws Exception {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        assertNull(FileContentPolicy.detectImage(bytes));
        var response = new MockHttpServletResponse();
        FileContentPolicy.writeResponse(new ByteArrayInputStream(bytes), response, "avatar.png", false, "application/octet-stream");
        assertEquals("application/octet-stream", response.getContentType());
        assertTrue(response.getHeader("Content-Disposition").startsWith("attachment;"));
        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
        assertEquals("default-src 'none'; sandbox", response.getHeader("Content-Security-Policy"));
        assertArrayEquals(bytes, response.getContentAsByteArray());
    }

    @Test
    void 图片下载和超出预览上限的附件均完整返回() throws Exception {
        byte[] image = image("png");
        var download = new MockHttpServletResponse();
        FileContentPolicy.writeResponse(new ByteArrayInputStream(image), download, "中文.png", true, "image/png");
        assertEquals("application/octet-stream", download.getContentType());
        assertTrue(download.getHeader("Content-Disposition").startsWith("attachment;"));
        assertArrayEquals(image, download.getContentAsByteArray());

        byte[] large = new byte[FileContentPolicy.MAX_IMAGE_BYTES + 100];
        Arrays.fill(large, (byte) 7);
        var response = new MockHttpServletResponse();
        FileContentPolicy.writeResponse(new ByteArrayInputStream(large), response, "large.png", false, "application/octet-stream");
        assertEquals("application/octet-stream", response.getContentType());
        assertArrayEquals(large, response.getContentAsByteArray());
    }

    @Test
    void 已校验图片直接流式预览不再读取前缀检测内容() throws Exception {
        byte[] bytes = image("png");
        var input = new ByteArrayInputStream(bytes) {
            @Override public byte[] readNBytes(int length) {
                throw new AssertionError("预览不能再次缓存完整图片并检测内容");
            }
        };
        var response = new MockHttpServletResponse();
        FileContentPolicy.writeResponse(input, response, "original.png", false, "image/png");
        assertEquals("image/png", response.getContentType());
        assertTrue(response.getHeader("Content-Disposition").startsWith("inline;"));
        assertArrayEquals(bytes, response.getContentAsByteArray());
    }

    @ParameterizedTest
    @ValueSource(strings = {"text/html", "image/svg+xml", "application/pdf", "image/avif", ""})
    void 未支持的存储类型不能内联(String contentType) throws Exception {
        var response = new MockHttpServletResponse();
        FileContentPolicy.writeResponse(new ByteArrayInputStream(image("png")), response, "image.png", false, contentType);
        assertEquals("application/octet-stream", response.getContentType());
        assertTrue(response.getHeader("Content-Disposition").startsWith("attachment;"));
    }

    @Test
    void 超大像素和截断的图片不会通过验证() {
        byte[] png = image("png");
        assertNull(FileContentPolicy.detectImage(Arrays.copyOf(png, 33)));
        // 修改合法 PNG 的 IHDR 并重算 CRC，确保在解码分配内存前拦截尺寸。
        ByteBuffer.wrap(png).putInt(16, 100_000).putInt(20, 100_000);
        CRC32 crc = new CRC32();
        crc.update(png, 12, 17);
        ByteBuffer.wrap(png).putInt(29, (int) crc.getValue());
        assertNull(FileContentPolicy.detectImage(png));
        assertThrows(IllegalArgumentException.class, () -> FileContentPolicy.requireImage(png));
    }
}
