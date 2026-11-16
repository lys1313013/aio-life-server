package top.aiolife.record.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import top.aiolife.config.MinioConfig;
import top.aiolife.core.lock.StorageObjectLock;
import top.aiolife.core.util.MinioUtil;
import top.aiolife.record.enums.FileBizType;
import top.aiolife.record.mapper.*;
import top.aiolife.record.service.*;
import top.aiolife.support.AttachmentDatabaseFixture;
import top.aiolife.system.mapper.StorageObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static top.aiolife.support.ImageFixtures.image;

class FileServiceImplTest extends AttachmentDatabaseFixture {
    private MinioUtil minio;
    private FileServiceImpl files;
    @BeforeEach void setup() throws Exception {
        minio=mock(MinioUtil.class);var stored=new HashSet<String>();
        when(minio.objectExists(anyString(),anyString())).thenAnswer(c->stored.contains(c.getArgument(1)));
        doAnswer(c->{stored.add(c.getArgument(1));return null;}).when(minio).putObject(anyString(),anyString(),any(),anyLong(),anyString());
        when(minio.getFilePreviewUrl(anyString())).thenAnswer(c->"/file/preview/"+c.getArgument(0));
        var config=new MinioConfig();config.setBucketName("fixture");
        var storage=proxy(new VideoCoverStorage(mapper(StorageObjectMapper.class),mock(StorageObjectLock.class),minio,config));
        files=new FileServiceImpl(minio,storage,mapper(StorageObjectMapper.class),mock(ExternalResourceService.class));
        ReflectionTestUtils.setField(files,"baseMapper",mapper(IFileMapper.class));files=proxy(files);
    }
    @Test void 相同字节跨用户上传只保存一次归属和文件名各自独立() throws Exception {
        try(var auth=mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsLong).thenReturn(11L);
            var first=files.upload(new MockMultipartFile("file","first.png","text/html",image("png")),FileBizType.AVATAR);
            auth.when(StpUtil::getLoginIdAsLong).thenReturn(22L);
            var second=files.upload(new MockMultipartFile("file","second.png","image/png",image("png")),FileBizType.DEVICE);
            assertNotEquals(first.getId(),second.getId());assertEquals("image/png",first.getFileType());
            assertEquals("first.png",first.getFileName());assertEquals("second.png",second.getFileName());
            var a=files.getByKey(first.getId());var b=files.getByKey(second.getId());
            assertEquals(4,UUID.fromString(first.getId()).version());
            assertNotEquals(first.getId(),a.getId());assertEquals(first.getId(),a.getFileKey());
            assertThrows(IllegalArgumentException.class, () -> files.getByKey(a.getId()));
            assertEquals(a.getStorageObjectId(),b.getStorageObjectId());assertEquals(11L,a.getUserId());assertEquals(22L,b.getUserId());
            assertEquals(0,a.getIsPublic());assertEquals(0,b.getIsPublic());assertEquals("device_cover",b.getBizType());
            assertEquals(1,mapper(StorageObjectMapper.class).selectCount(null));
            verify(minio,times(1)).putObject(anyString(),anyString(),any(),anyLong(),eq("image/png"));
        }
    }
    @Test void 上传事务回滚保留共享字节不误删已有文件() throws Exception {
        try(var auth=mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsLong).thenReturn(11L);
            transaction.executeWithoutResult(t->{files.upload(new MockMultipartFile("file","file.png","image/png",image("png")),FileBizType.AVATAR);t.setRollbackOnly();});
            assertEquals(0,mapper(IFileMapper.class).selectCount(null));assertEquals(0,mapper(StorageObjectMapper.class).selectCount(null));
            verify(minio,never()).removeObject(anyString(),anyString());
        }
    }
    @Test void 空文件拒绝且不访问对象存储() {
        assertThrows(IllegalArgumentException.class,()->files.upload(new MockMultipartFile("file",new byte[0]),FileBizType.DEVICE));
        verifyNoInteractions(minio);
    }
}
