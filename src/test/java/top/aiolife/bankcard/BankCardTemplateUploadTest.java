package top.aiolife.bankcard;

import cn.dev33.satoken.stp.StpUtil;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import top.aiolife.config.MinioConfig;
import top.aiolife.core.util.MinioUtil;
import top.aiolife.record.enums.FileBizType;
import top.aiolife.record.pojo.entity.FileEntity;
import top.aiolife.record.service.impl.FileServiceImpl;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class BankCardTemplateUploadTest {
    @Test void 系统目录上传JPEG会转为PNG且文件记录保持私有() throws Exception {
        var minio=mock(MinioUtil.class);var config=new MinioConfig();config.setBucketName("test-bucket");
        var saved=new AtomicReference<FileEntity>();
        var service=new FileServiceImpl(minio,config) {
            @Override public boolean save(FileEntity entity) { entity.setId("a".repeat(32));saved.set(entity);return true; }
        };
        var source=new ByteArrayOutputStream();
        ImageIO.write(new java.awt.image.BufferedImage(96,60,java.awt.image.BufferedImage.TYPE_INT_RGB),"jpeg",source);
        doAnswer(call -> {
            assertTrue(call.getArgument(1,String.class).matches("system/bank-card-covers/[a-f0-9-]+\\.png"));
            byte[] body=call.getArgument(2,InputStream.class).readAllBytes();
            assertEquals(0x89,body[0]&0xff);assertEquals('P',body[1]);
            assertNotNull(ImageIO.read(new java.io.ByteArrayInputStream(body)));
            assertEquals(body.length,call.getArgument(3,Long.class));return null;
        }).when(minio).putObject(eq("test-bucket"),anyString(),any(),anyLong(),eq("image/png"));
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try(var login=mockStatic(StpUtil.class)) {
            login.when(StpUtil::getLoginIdAsLong).thenReturn(1L);
            service.upload(new MockMultipartFile("file","input.jpg","image/jpeg",source.toByteArray()),FileBizType.BANK_CARD_TEMPLATE_COVER);
            login.verify(()->StpUtil.checkRole("admin"));
        } finally { org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization(); }
        assertEquals("image/png",saved.get().getFileType());assertEquals(0,saved.get().getIsPublic());
        assertNull(saved.get().getBizId());assertEquals(1L,saved.get().getCreateUser());
        verify(minio).putObject(eq("test-bucket"),anyString(),any(),anyLong(),eq("image/png"));
    }
    @Test void 通用上传也必须检查管理员且不能绕过模板绑定事务() {
        var minio=mock(MinioUtil.class);var service=new FileServiceImpl(minio,new MinioConfig());
        try(var login=mockStatic(StpUtil.class)) {
            login.when(()->StpUtil.checkRole("admin")).thenThrow(new IllegalArgumentException("无权限"));
            assertThrows(IllegalArgumentException.class,()->service.upload(new MockMultipartFile("file",new byte[]{1}),FileBizType.BANK_CARD_TEMPLATE_COVER));
        }
        assertThrows(IllegalArgumentException.class,()->service.bindBizId(List.of("a".repeat(32)),"bank_card_template_cover",1L));
        assertThrows(IllegalArgumentException.class,()->service.uploadFromUrl("https://example.com/image.png",FileBizType.BANK_CARD_TEMPLATE_COVER));
        verifyNoInteractions(minio);
    }
}
