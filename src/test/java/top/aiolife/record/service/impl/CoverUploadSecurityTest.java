package top.aiolife.record.service.impl;

import top.aiolife.record.service.RecordImageService;

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

    @ParameterizedTest
    @EnumSource(value=FileBizType.class,names={"MOVIE","READ_RECORD"})
    void 非白名单或不稳定路径在下载之前拒绝(FileBizType type) {
        var downloader=new top.aiolife.record.service.VideoCoverDownloader();
        var sources=new top.aiolife.record.service.ExternalResourceService(
            mock(top.aiolife.record.mapper.ExternalResourceMapper.class),downloader,new DoubanCoverUrlPolicy("doubanio.com"),
            mock(top.aiolife.record.service.VideoCoverStorage.class),mock(org.springframework.transaction.PlatformTransactionManager.class));
        for(String url:java.util.List.of("https://127.0.0.1/cover.jpg","https://img.doubanio.com/unknown.jpg","https://img.doubanio.com.evil.test/view/photo/l/public/p1.jpg"))
            assertThrows(IllegalArgumentException.class,()->sources.identity(url,type));
        var identity=sources.identity("https://img1.doubanio.com/view/photo/l/public/p123.jpg?signature=x",type);
        assertEquals("p123",identity.getExternalKey());assertEquals("l",identity.getVariant());
        assertEquals(type==FileBizType.MOVIE?"movie_poster":"book_cover",identity.getResourceType());
    }

    @Test
    void ensureCoverUploaded_自定义FileId不触发远程抓取() {
        var uploader = mock(IFileService.class);
        var movie = new MovieReq();
        movie.setFileId("uploaded-movie-id");
        movie.setCoverImgUrl("https://example.com/legacy-cover.jpg");
        new MovieServiceImpl(uploader, org.mockito.Mockito.mock(RecordImageService.class)).ensureCoverUploaded(movie, true);
        var read = new ReadRecordReq();
        read.setFileId("uploaded-read-id");
        read.setCoverImgUrl("https://example.com/legacy-cover.jpg");
        ReflectionTestUtils.invokeMethod(new ReadRecordServiceImpl(uploader, org.mockito.Mockito.mock(RecordImageService.class)), "ensureCoverUploaded", read, true);
        verifyNoInteractions(uploader);
        assertEquals("uploaded-movie-id", movie.getFileId());
        assertEquals("uploaded-read-id", read.getFileId());
    }

    @Test
    void ensureCoverUploaded_安全拒绝不会被包装成Minio故障且解析不回填危险URL() {
        var uploader = mock(IFileService.class);
        var error = new IllegalArgumentException("自定义封面请上传图片");
        when(uploader.uploadFromUrl(anyString(), any())).thenThrow(error);
        var movieService = new MovieServiceImpl(uploader, org.mockito.Mockito.mock(RecordImageService.class));
        var movie = new MovieReq();
        movie.setCoverImgUrl("https://127.0.0.1/cover.jpg");
        assertSame(error, assertThrows(IllegalArgumentException.class,
                () -> movieService.ensureCoverUploaded(movie, true)));
        movieService.ensureCoverUploaded(movie, false);
        assertNull(movie.getCoverImgUrl());
        var readService = new ReadRecordServiceImpl(uploader, org.mockito.Mockito.mock(RecordImageService.class));
        var read = new ReadRecordReq();
        read.setCoverImgUrl("https://127.0.0.1/cover.jpg");
        assertSame(error, assertThrows(IllegalArgumentException.class,
                () -> ReflectionTestUtils.invokeMethod(readService, "ensureCoverUploaded", read, true)));
        ReflectionTestUtils.invokeMethod(readService, "ensureCoverUploaded", read, false);
        assertNull(read.getCoverImgUrl());
    }
}
