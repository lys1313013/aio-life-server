package top.aiolife.record.service;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.List;
import javax.net.ssl.SSLException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import top.aiolife.record.mapper.ImageImportTaskMapper;
import top.aiolife.record.pojo.entity.ImageImportTaskEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VideoCoverWorkerTest {
    @Test void 下载错误保留受控分类且不透传异常中的敏感地址() {
        assertFailure(new IllegalStateException("download",new UnknownHostException()),false,"IMAGE_DNS_FAILED");
        assertFailure(new IllegalStateException("download",new SocketTimeoutException()),false,"IMAGE_DOWNLOAD_TIMEOUT");
        assertFailure(new IllegalStateException("download",new ConnectException()),false,"IMAGE_CONNECT_FAILED");
        assertFailure(new IllegalStateException("download",new SSLException("secret")),false,"IMAGE_TLS_FAILED");
        assertFailure(new IllegalStateException("IMAGE_HTTP_403"),false,"IMAGE_HTTP_403");
        assertFailure(new IllegalArgumentException("invalid"),false,"INVALID_IMAGE_SOURCE");
        assertFailure(new IllegalStateException("https://private.invalid/?token=secret",new IOException("secret")),false,"IMAGE_DOWNLOAD_FAILED");
    }

    @Test void 下载完成后区分对象存储和数据库失败() {
        assertFailure(new IllegalStateException("storage secret"),true,"IMAGE_STORAGE_FAILED");
        assertFailure(new DataAccessResourceFailureException("SQL secret"),true,"IMAGE_DATABASE_FAILED");
    }

    private void assertFailure(RuntimeException exception, boolean storage, String expected) {
        var tasks=mock(ImageImportTaskMapper.class);
        var downloader=mock(VideoCoverDownloader.class);
        var service=mock(VideoCoverService.class);
        var task=new ImageImportTaskEntity();
        task.setId(1L); task.setAttempts(0); task.setSourceUrl("https://i0.hdslb.com/bfs/archive/test.jpg");
        when(tasks.due()).thenReturn(List.of(task));
        when(tasks.claim(eq(1L),anyString())).thenReturn(1);
        byte[] bytes={1,2};
        if (storage) {
            when(downloader.download(task.getSourceUrl())).thenReturn(bytes);
            doThrow(exception).when(service).complete(eq(1L),anyString(),eq(bytes));
        } else when(downloader.download(task.getSourceUrl())).thenThrow(exception);
        assertEquals(1,new VideoCoverWorker(tasks,downloader,service).runBatch());
        verify(service).failed(eq(1L),anyString(),eq(expected));
        if (!storage) verify(service,never()).complete(eq(1L),anyString(),any());
    }
}
