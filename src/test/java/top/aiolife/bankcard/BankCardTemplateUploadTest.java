package top.aiolife.bankcard;

import cn.dev33.satoken.stp.StpUtil;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.aiolife.config.MinioConfig;
import top.aiolife.core.lock.StorageObjectLock;
import top.aiolife.core.util.MinioUtil;
import top.aiolife.record.enums.FileBizType;
import top.aiolife.record.pojo.entity.FileEntity;
import top.aiolife.record.service.DoubanCoverUrlPolicy;
import top.aiolife.record.service.impl.FileServiceImpl;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class BankCardTemplateUploadTest {
    @Test void 系统目录上传JPEG会转为PNG且文件记录保持私有() throws Exception {
        var minio=mock(MinioUtil.class);var config=new MinioConfig();config.setBucketName("test-bucket");
        var saved=new AtomicReference<FileEntity>();
        var storage=mock(top.aiolife.record.service.VideoCoverStorage.class);
        var objects=mock(top.aiolife.system.mapper.StorageObjectMapper.class);
        var object=new top.aiolife.system.pojo.entity.StorageObjectEntity();object.setId(11L);object.setContentType("image/png");object.setFileSize(68L);
        when(objects.selectById(11L)).thenReturn(object);
        when(storage.store(any(),anyLong())).thenAnswer(call->{
            byte[] body=call.getArgument(0);assertEquals(0x89,body[0]&255);assertNotNull(ImageIO.read(new ByteArrayInputStream(body)));return object;
        });
        var service=new FileServiceImpl(minio,storage,objects,org.mockito.Mockito.mock(top.aiolife.record.service.ExternalResourceService.class)) {
            @Override public boolean save(FileEntity entity) { entity.setId("a".repeat(32));saved.set(entity);return true; }
        };
        var source=new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(96,60,BufferedImage.TYPE_INT_RGB),"jpeg",source);
        TransactionSynchronizationManager.initSynchronization();
        try(var login=mockStatic(StpUtil.class)) {
            login.when(StpUtil::getLoginIdAsLong).thenReturn(1L);
            service.upload(new MockMultipartFile("file","input.jpg","image/jpeg",source.toByteArray()),FileBizType.BANK_CARD_TEMPLATE_COVER);
            login.verify(()->StpUtil.checkRole("admin"));
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
        assertNull(saved.get().getUserId());assertEquals(0,saved.get().getIsPublic());
        assertNull(saved.get().getBizId());assertEquals(1L,saved.get().getCreateUser());
        verify(storage).store(any(),eq(1L));
    }
    @Test void 通用上传也必须检查管理员且不能绕过模板绑定事务() {
        var minio=mock(MinioUtil.class);var service=new FileServiceImpl(minio,org.mockito.Mockito.mock(top.aiolife.record.service.VideoCoverStorage.class),org.mockito.Mockito.mock(top.aiolife.system.mapper.StorageObjectMapper.class),org.mockito.Mockito.mock(top.aiolife.record.service.ExternalResourceService.class));
        try(var login=mockStatic(StpUtil.class)) {
            login.when(()->StpUtil.checkRole("admin")).thenThrow(new IllegalArgumentException("无权限"));
            assertThrows(IllegalArgumentException.class,()->service.upload(new MockMultipartFile("file",new byte[]{1}),FileBizType.BANK_CARD_TEMPLATE_COVER));
        }
        assertThrows(IllegalArgumentException.class,()->service.bindBizId(List.of("a".repeat(32)),"bank_card_template_cover",1L));
        assertThrows(IllegalArgumentException.class,()->service.uploadFromUrl("https://example.com/image.png",FileBizType.BANK_CARD_TEMPLATE_COVER));
        verifyNoInteractions(minio);
    }
}
