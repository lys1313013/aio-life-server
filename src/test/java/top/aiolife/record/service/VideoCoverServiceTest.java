package top.aiolife.record.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.*;
import top.aiolife.config.MinioConfig;
import top.aiolife.core.lock.StorageObjectLock;
import top.aiolife.core.util.MinioUtil;
import top.aiolife.record.mapper.*;
import top.aiolife.record.pojo.entity.*;
import top.aiolife.support.AttachmentDatabaseFixture;
import top.aiolife.system.mapper.StorageObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static top.aiolife.support.ImageFixtures.image;

/** 真实PostgreSQL、真实Mapper与事务；外网和对象存储由替身隔离。 */
class VideoCoverServiceTest extends AttachmentDatabaseFixture {
    private IFileMapper files;
    private ImageImportTaskMapper tasks;
    private ExternalResourceMapper sources;
    private VideoCoverService service;
    private ExternalResourceService resources;
    private VideoCoverStorage storage;
    private VideoCoverDownloader downloader;
    private MinioUtil minio;
    private final String url="https://i0.hdslb.com/bfs/archive/example.jpg";
    @BeforeEach void setup() throws Exception {
        files=mapper(IFileMapper.class);tasks=mapper(ImageImportTaskMapper.class);sources=mapper(ExternalResourceMapper.class);
        minio=mock(MinioUtil.class);var config=new MinioConfig();config.setBucketName("fixture");
        var stored=new HashSet<String>();
        when(minio.objectExists(anyString(),anyString())).thenAnswer(c->stored.contains(c.getArgument(1)));
        doAnswer(c->{stored.add(c.getArgument(1));return null;}).when(minio).putObject(anyString(),anyString(),any(),anyLong(),anyString());
        storage=proxy(new VideoCoverStorage(mapper(StorageObjectMapper.class),mock(StorageObjectLock.class),minio,config));
        downloader=spy(new VideoCoverDownloader()); doReturn(image("png")).when(downloader).download(anyString());
        resources=new ExternalResourceService(sources,downloader,new DoubanCoverUrlPolicy("doubanio.com"),storage,manager);
        service=proxy(new VideoCoverService(mapper(IBVideoMapper.class),tasks,files,sources,resources,storage));
    }
    private void video(long id,long owner) { jdbc.update("INSERT INTO b_video(id,user_id,title,url,duration_seconds) VALUES(?,?,'fixture','https://example.test',60)",id,owner); }
    private FileEntity cover(long id) { return files.selectOne(new LambdaQueryWrapper<FileEntity>().eq(FileEntity::getBizType,"b_video_cover").eq(FileEntity::getBizId,id)); }
    private void run() { new VideoCoverWorker(sources,resources,tasks,service).runBatch(); }
    @Test void 远程来源伪装图片在写入和发布前失败() {
        video(1,11);
        doReturn("<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8))
            .when(downloader).download(anyString());
        service.enqueue(1,11,url,false);run();
        assertEquals("FAILED",sources.selectList(null).getFirst().getState());
        assertEquals("INVALID_IMAGE_SOURCE",sources.selectList(null).getFirst().getErrorCode());
        assertNull(cover(1));assertEquals(0,mapper(StorageObjectMapper.class).selectCount(null));
        verifyNoInteractions(minio);
    }
    @Test void 同一来源跨用户只下载一次但各自持有附件() throws Exception {
        video(1,11);video(2,22);service.enqueue(1,11,url,false);service.enqueue(2,22,url,false);run();
        assertEquals(1,sources.selectCount(null));assertEquals(1,mapper(StorageObjectMapper.class).selectCount(null));assertEquals(2,files.selectCount(null));
        assertNotEquals(cover(1).getId(),cover(2).getId());assertEquals(cover(1).getStorageObjectId(),cover(2).getStorageObjectId());
        verify(downloader,times(1)).download(url);
        verify(minio,times(1)).putObject(eq("fixture"),matches("objects/[0-9a-f]{2}/[0-9a-f]{64}\\.png"),any(InputStream.class),anyLong(),eq("image/png"));
        service.enqueue(1,11,url,false);run();verify(downloader,times(1)).download(url);
        var row=mapper(IBVideoMapper.class).selectById(1);service.fillCoverFiles(11,List.of(row));assertEquals("READY",row.getCoverState());
        assertEquals(cover(1).getFileKey(),row.getCoverFileId());assertTrue(top.aiolife.core.id.AttachmentKey.isValid(row.getCoverFileId()));
        assertEquals(cover(1).getId(),tasks.latest("b_video_cover",1).getResultAttachmentId());
        service.fillCoverFiles(22,List.of(row));assertNull(row.getCoverFileId());
    }
    @Test void 更换和清空会取消旧任务且旧完成不能覆盖() {
        video(1,11);service.enqueue(1,11,url,false);var old=tasks.selectList(null).getFirst();
        service.enqueue(1,11,"https://i0.hdslb.com/bfs/archive/new.jpg",false);
        resources.fetch(old.getExternalResourceId());service.settle(old.getId());assertNull(cover(1));
        assertEquals("CANCELLED",tasks.selectById(old.getId()).getState());run();assertNotNull(cover(1));
        service.enqueue(1,11,"",false);run();assertNull(cover(1));
        var row=mapper(IBVideoMapper.class).selectById(1);service.fillCoverFiles(11,List.of(row));assertEquals("NONE",row.getCoverState());
    }
    @Test void 换成已缓存来源也取消在途请求() {
        video(1,11);video(2,22);service.enqueue(2,22,url,false);run();
        service.enqueue(1,11,"https://i0.hdslb.com/bfs/archive/old.jpg",false);var old=tasks.latest("b_video_cover",1);
        service.enqueue(1,11,url,false);String selected=cover(1).getId();
        resources.fetch(old.getExternalResourceId());service.settle(old.getId());assertEquals(selected,cover(1).getId());
    }
    @Test void 相同来源不重复排队且业务回滚保留旧请求() {
        video(1,11);service.enqueue(1,11,url,false);service.enqueue(1,11,url,false);assertEquals(1,tasks.selectCount(null));
        transaction.executeWithoutResult(t->{service.enqueue(1,11,"https://i0.hdslb.com/bfs/archive/new.jpg",false);t.setRollbackOnly();});
        assertEquals(1,tasks.selectCount(null));assertEquals("PENDING",tasks.selectList(null).getFirst().getState());
    }
    @Test void 失败达到上限后可重试且成功来源不能强制刷新() throws Exception {
        video(1,11);doThrow(new IllegalStateException("secret URL")).when(downloader).download(anyString());
        service.enqueue(1,11,url,false);
        for(int i=0;i<5;i++) { jdbc.update("UPDATE external_resource SET next_attempt_at=NULL");run(); }
        var failed=tasks.latest("b_video_cover",1);assertEquals("FAILED",failed.getState());assertNull(cover(1));
        doReturn(image("png")).when(downloader).download(anyString());service.retry(1,11);run();assertNotNull(cover(1));
        clearInvocations(downloader);service.retry(1,11);resources.retryFailed(failed.getExternalResourceId());run();
        verify(downloader,never()).download(anyString());
    }
    @Test void 删除业务后任务不会发布附件且共享对象不随业务删除() {
        video(1,11);service.enqueue(1,11,url,false);var old=tasks.latest("b_video_cover",1);
        service.delete(1,11);mapper(IBVideoMapper.class).deleteById(1);resources.fetch(old.getExternalResourceId());service.settle(old.getId());
        assertNull(cover(1));assertEquals("CANCELLED",tasks.selectById(old.getId()).getState());
        assertEquals(1,mapper(StorageObjectMapper.class).selectCount(null));
    }
    @Test void 豆瓣预览建立本人待绑定附件且成功来源长期复用() throws Exception {
        doReturn(image("png")).when(downloader).downloadDouban(anyString());
        var uploader=new top.aiolife.record.service.impl.FileServiceImpl(minio,storage,mapper(StorageObjectMapper.class),resources);
        org.springframework.test.util.ReflectionTestUtils.setField(uploader,"baseMapper",files);uploader=proxy(uploader);
        try(var auth=mockStatic(cn.dev33.satoken.stp.StpUtil.class)) {
            auth.when(cn.dev33.satoken.stp.StpUtil::getLoginIdAsLong).thenReturn(11L);
            var first=uploader.uploadFromUrl("https://img1.doubanio.com/view/photo/l/public/p123.jpg",top.aiolife.record.enums.FileBizType.MOVIE);
            auth.when(cn.dev33.satoken.stp.StpUtil::getLoginIdAsLong).thenReturn(22L);
            var second=uploader.uploadFromUrl("https://img2.doubanio.com/view/photo/l/public/p123.jpg?sign=changed",top.aiolife.record.enums.FileBizType.MOVIE);
            assertNotEquals(first.getId(),second.getId());assertNull(files.selectOne(new LambdaQueryWrapper<FileEntity>().eq(FileEntity::getFileKey,first.getId())).getBizId());
            assertEquals(11L,files.selectOne(new LambdaQueryWrapper<FileEntity>().eq(FileEntity::getFileKey,first.getId())).getUserId());assertEquals(22L,files.selectOne(new LambdaQueryWrapper<FileEntity>().eq(FileEntity::getFileKey,second.getId())).getUserId());
            verify(downloader,times(1)).downloadDouban(anyString());assertEquals(1,sources.selectCount(null));
        }
    }
}
