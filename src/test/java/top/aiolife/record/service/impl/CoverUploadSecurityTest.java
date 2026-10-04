package top.aiolife.record.service.impl;

import java.io.ByteArrayInputStream;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.aiolife.config.MinioConfig;
import top.aiolife.core.lock.StorageObjectLock;
import top.aiolife.core.util.MinioUtil;
import top.aiolife.record.enums.FileBizType;
import top.aiolife.record.pojo.entity.FileEntity;
import top.aiolife.record.pojo.req.MovieReq;
import top.aiolife.record.pojo.req.ReadRecordReq;
import top.aiolife.record.service.DoubanCoverUrlPolicy;
import top.aiolife.record.service.IFileService;
import top.aiolife.support.ImageFixtures;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CoverUploadSecurityTest {
    @BeforeEach
    void initSynchronization() {
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void clearSynchronization() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    private final MinioUtil minio = mock(MinioUtil.class);
    private final FileServiceImpl files = new FileServiceImpl(minio, new MinioConfig(),
            mock(StorageObjectLock.class), new DoubanCoverUrlPolicy("doubanio.com")) {
        @Override
        public boolean save(FileEntity entity) {
            entity.setId("cover-file-id");
            return true;
        }
    };

    @ParameterizedTest
    @EnumSource(value = FileBizType.class, names = {"MOVIE", "READ_RECORD"})
    void uploadFromUrl_非法来源在发出HTTP前拒绝(FileBizType bizType) {
        try (var http = mockStatic(HttpRequest.class)) {
            assertThrows(IllegalArgumentException.class,
                    () -> files.uploadFromUrl("https://127.0.0.1/cover.jpg", bizType));
            http.verifyNoInteractions();
            verifyNoInteractions(minio);
        }
    }

    @Test
    void uploadFromUrl_拒绝重定向且关闭响应() {
        var request = mock(HttpRequest.class, RETURNS_SELF);
        when(request.header(anyString(), anyString())).thenReturn(request);
        var response = mock(HttpResponse.class);
        when(request.executeAsync()).thenReturn(response);
        when(response.getStatus()).thenReturn(302);
        try (var http = mockStatic(HttpRequest.class); var auth = mockStatic(StpUtil.class)) {
            http.when(() -> HttpRequest.get(anyString())).thenReturn(request);
            auth.when(StpUtil::getLoginIdAsLong).thenReturn(1L);
            assertThrows(IllegalStateException.class,
                    () -> files.uploadFromUrl("https://img1.doubanio.com/cover.jpg", FileBizType.MOVIE));
            verify(request).setFollowRedirects(false);
            verify(response, never()).bodyBytes();
            verify(response).close();
            verifyNoInteractions(minio);
        }
    }

    @ParameterizedTest
    @EnumSource(value = FileBizType.class, names = {"MOVIE", "READ_RECORD"})
    void uploadFromUrl_白名单下载仍保存FileId(FileBizType bizType) throws Exception {
        var request = mock(HttpRequest.class, RETURNS_SELF);
        when(request.header(anyString(), anyString())).thenReturn(request);
        var response = mock(HttpResponse.class);
        when(request.executeAsync()).thenReturn(response);
        when(response.getStatus()).thenReturn(200);
        byte[] content = ImageFixtures.image("jpeg");
        when(response.bodyStream()).thenReturn(new ByteArrayInputStream(content));
        try (var http = mockStatic(HttpRequest.class); var auth = mockStatic(StpUtil.class)) {
            http.when(() -> HttpRequest.get(anyString())).thenReturn(request);
            auth.when(StpUtil::getLoginIdAsLong).thenReturn(1L);
            assertEquals("cover-file-id",
                    files.uploadFromUrl("https://img1.doubanio.com/cover.jpg", bizType).getId());
            verify(request).setFollowRedirects(false);
            verify(minio).putObject(eq("aiolife"), anyString(), any(), eq((long) content.length), eq("image/jpeg"));
            verify(response).close();
        }
    }

    @Test
    void ensureCoverUploaded_自定义FileId不触发远程抓取() {
        var uploader = mock(IFileService.class);
        var movie = new MovieReq();
        movie.setFileId("uploaded-movie-id");
        movie.setCoverImgUrl("https://example.com/legacy-cover.jpg");
        new MovieServiceImpl(uploader).ensureCoverUploaded(movie, true);
        var read = new ReadRecordReq();
        read.setFileId("uploaded-read-id");
        read.setCoverImgUrl("https://example.com/legacy-cover.jpg");
        ReflectionTestUtils.invokeMethod(new ReadRecordServiceImpl(uploader), "ensureCoverUploaded", read, true);
        verifyNoInteractions(uploader);
        assertEquals("uploaded-movie-id", movie.getFileId());
        assertEquals("uploaded-read-id", read.getFileId());
    }

    @Test
    void ensureCoverUploaded_安全拒绝不会被包装成Minio故障且解析不回填危险URL() {
        var uploader = mock(IFileService.class);
        var error = new IllegalArgumentException("自定义封面请上传图片");
        when(uploader.uploadFromUrl(anyString(), any())).thenThrow(error);
        var movieService = new MovieServiceImpl(uploader);
        var movie = new MovieReq();
        movie.setCoverImgUrl("https://127.0.0.1/cover.jpg");
        assertSame(error, assertThrows(IllegalArgumentException.class,
                () -> movieService.ensureCoverUploaded(movie, true)));
        movieService.ensureCoverUploaded(movie, false);
        assertNull(movie.getCoverImgUrl());
        var readService = new ReadRecordServiceImpl(uploader);
        var read = new ReadRecordReq();
        read.setCoverImgUrl("https://127.0.0.1/cover.jpg");
        assertSame(error, assertThrows(IllegalArgumentException.class,
                () -> ReflectionTestUtils.invokeMethod(readService, "ensureCoverUploaded", read, true)));
        ReflectionTestUtils.invokeMethod(readService, "ensureCoverUploaded", read, false);
        assertNull(read.getCoverImgUrl());
    }
}
