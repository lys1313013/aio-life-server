package top.aiolife.record.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.aiolife.config.MinioConfig;
import top.aiolife.core.lock.StorageObjectLock;
import top.aiolife.core.util.FileContentPolicy;
import top.aiolife.core.util.MinioUtil;
import top.aiolife.record.enums.FileBizType;
import top.aiolife.record.pojo.entity.FileEntity;
import top.aiolife.record.service.DoubanCoverUrlPolicy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static top.aiolife.support.ImageFixtures.image;

class FileUploadContentSecurityTest {
    private final MinioUtil minio = mock(MinioUtil.class);
    private final StorageObjectLock lock = mock(StorageObjectLock.class);
    private final AtomicReference<FileEntity> saved = new AtomicReference<>();
    private final FileServiceImpl service = new FileServiceImpl(minio, new MinioConfig(), lock,
            new DoubanCoverUrlPolicy("doubanio.com")) {
        @Override public boolean save(FileEntity entity) {
            entity.setId("a".repeat(32));
            saved.set(entity);
            return true;
        }
    };

    @BeforeEach void setup() { TransactionSynchronizationManager.initSynchronization(); }
    @AfterEach void cleanup() { TransactionSynchronizationManager.clearSynchronization(); }

    @ParameterizedTest
    @EnumSource(value = FileBizType.class, names = {"AVATAR", "WARDROBE_ITEM", "MOVIE", "READ_RECORD", "BANK_CARD_COVER"})
    void 图片业务在写存储之前拒绝伪装成JPEG的HTML(FileBizType bizType) {
        var file = new MockMultipartFile("file", "fake.jpg", "image/jpeg", "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class, () -> service.upload(file, bizType));
        verifyNoInteractions(minio, lock);
        assertNull(saved.get());
    }

    @ParameterizedTest
    @ValueSource(strings = {"text/html", "image/svg+xml", "application/octet-stream"})
    void 头像不允许通过声明不同MIME上传SVG(String declaredType) {
        var file = new MockMultipartFile("file", "avatar.png", declaredType,
                "<svg xmlns='http://www.w3.org/2000/svg' onload='alert(1)'/>".getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class, () -> service.upload(file, FileBizType.AVATAR));
        verifyNoInteractions(minio, lock);
    }

    @Test
    void 真实图片的数据库类型对象后缀和Minio类型都由内容决定() throws Exception {
        byte[] bytes = image("png");
        doAnswer(call -> {
            assertTrue(call.getArgument(1, String.class).endsWith(".png"));
            assertArrayEquals(bytes, call.getArgument(2, InputStream.class).readAllBytes());
            return null;
        }).when(minio).putObject(eq("aiolife"), anyString(), any(), eq((long) bytes.length), eq("image/png"));
        try (var auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsLong).thenReturn(1L);
            service.upload(new MockMultipartFile("file", "fake.html", "text/html", bytes), FileBizType.AVATAR);
        }
        assertEquals("image/png", saved.get().getFileType());
        assertTrue(saved.get().getFileName().endsWith(".png"));
        verify(minio).putObject(eq("aiolife"), anyString(), any(), eq((long) bytes.length), eq("image/png"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"text/html", "application/pdf", "image/svg+xml"})
    void 通用附件仍可保存但存储类型固定为下载类型(String declaredType) throws Exception {
        byte[] bytes = "attachment content".getBytes(StandardCharsets.UTF_8);
        try (var auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsLong).thenReturn(1L);
            service.upload(new MockMultipartFile("file", "attachment.html", declaredType, bytes), FileBizType.HONOR_RECORD);
        }
        assertEquals("application/octet-stream", saved.get().getFileType());
        assertEquals(0, saved.get().getIsPublic());
        verify(minio).putObject(eq("aiolife"), anyString(), any(), eq((long) bytes.length), eq("application/octet-stream"));
    }

    @Test
    void 远程封面即使来源合法也必须校验真实图片() {
        var request = mock(HttpRequest.class, RETURNS_SELF);
        when(request.header(anyString(), anyString())).thenReturn(request);
        var response = mock(HttpResponse.class);
        when(request.executeAsync()).thenReturn(response);
        when(response.getStatus()).thenReturn(200);
        when(response.bodyStream()).thenReturn(new ByteArrayInputStream("<html>fake image</html>".getBytes(StandardCharsets.UTF_8)));
        try (var http = mockStatic(HttpRequest.class); var auth = mockStatic(StpUtil.class)) {
            http.when(() -> HttpRequest.get(anyString())).thenReturn(request);
            auth.when(StpUtil::getLoginIdAsLong).thenReturn(1L);
            assertThrows(IllegalArgumentException.class,
                    () -> service.uploadFromUrl("https://img1.doubanio.com/fake.jpg", FileBizType.MOVIE));
        }
        verify(response).close();
        verifyNoInteractions(minio, lock);
    }

    @Test
    void 超大上传在读取流或写存储前拒绝() throws Exception {
        var file = mock(org.springframework.web.multipart.MultipartFile.class);
        when(file.getSize()).thenReturn((long) FileContentPolicy.MAX_IMAGE_BYTES + 1);
        assertThrows(IllegalArgumentException.class, () -> service.upload(file, FileBizType.AVATAR));
        verify(file, never()).getInputStream();
        verifyNoInteractions(minio, lock);
    }
}
