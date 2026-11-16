package top.aiolife.record.service;

import java.util.List;
import org.junit.jupiter.api.Test;
import top.aiolife.record.mapper.*;
import top.aiolife.record.pojo.entity.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class VideoCoverWorkerTest {
    @Test void 来源下载和等待任务分开扫描任务无需领取即可恢复() {
        var sources=mock(ExternalResourceMapper.class);var resources=mock(ExternalResourceService.class);
        var jobs=mock(ImageImportTaskMapper.class);var service=mock(VideoCoverService.class);
        var source=new ExternalResourceEntity();source.setId(11L);var job=new ImageImportTaskEntity();job.setId(12L);
        when(sources.due()).thenReturn(List.of(source));when(jobs.due()).thenReturn(List.of(job));
        assertEquals(2,new VideoCoverWorker(sources,resources,jobs,service).runBatch());
        verify(resources).fetch(11L);verify(service).settle(12L);
    }
}
