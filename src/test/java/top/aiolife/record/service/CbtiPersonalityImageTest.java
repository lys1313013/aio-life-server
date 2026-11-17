package top.aiolife.record.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import top.aiolife.config.CbtiConfig;
import top.aiolife.core.util.MinioUtil;
import top.aiolife.record.mapper.IAttachmentMapper;
import top.aiolife.record.mapper.ICbtiPersonalityMapper;
import top.aiolife.record.mapper.ICbtiResultMapper;
import top.aiolife.record.pojo.entity.AttachmentEntity;
import top.aiolife.record.pojo.entity.CbtiPersonalityEntity;
import top.aiolife.record.service.impl.CbtiServiceImpl;
import top.aiolife.system.pojo.entity.StorageObjectEntity;
import top.aiolife.support.ImageFixtures;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CbtiPersonalityImageTest {
    @BeforeAll
    static void tableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "cbti-image"), AttachmentEntity.class);
    }

    private final ICbtiPersonalityMapper personalities = mock(ICbtiPersonalityMapper.class);
    private final IAttachmentMapper attachments = mock(IAttachmentMapper.class);
    private final VideoCoverStorage storage = mock(VideoCoverStorage.class);
    private final MinioUtil minio = mock(MinioUtil.class);
    private final CbtiConfig config = new CbtiConfig();
    private final CbtiServiceImpl service = new CbtiServiceImpl(personalities, mock(ICbtiResultMapper.class),
            attachments, storage, new ObjectMapper(), minio, config);

    private CbtiPersonalityEntity personality() {
        var entity = new CbtiPersonalityEntity();
        entity.setId(7L);
        entity.setCode("SUDO");
        entity.setIsDeleted(0);
        when(personalities.selectOne(any())).thenReturn(entity);
        when(personalities.lockById(7L)).thenReturn(7L);
        return entity;
    }

    private StorageObjectEntity object() {
        var object = new StorageObjectEntity();
        object.setId(99L);
        object.setContentHash("a".repeat(64));
        object.setContentType("image/png");
        return object;
    }

    @Test
    void 上传拒绝伪装图片且不写对象() {
        personality();
        var file = new MockMultipartFile("file", "fake.png", "image/png", "<svg/>".getBytes());
        assertThrows(IllegalArgumentException.class, () -> service.uploadPersonalityImage(1L, "SUDO", file));
        verifyNoInteractions(storage);
        verify(attachments, never()).insert(any(AttachmentEntity.class));
    }

    @Test
    void 上传按内容寻址保存并公开绑定到人格() {
        personality();
        var object = object();
        when(storage.store(any(), eq(1L), any())).thenReturn(object);
        when(attachments.selectWithObjectByBiz(eq("cbti_personality_image"), anyList())).thenReturn(List.of());
        when(minio.getPublicImageUrl(object.getContentHash(), object.getContentType())).thenReturn("http://host/api/f/hash.png");

        var file = new MockMultipartFile("file", "sudo.png", "image/png", ImageFixtures.image("png"));
        var result = service.uploadPersonalityImage(1L, "SUDO", file);

        assertEquals("http://host/api/f/hash.png", result.get("imageUrl"));
        var captor = org.mockito.ArgumentCaptor.forClass(AttachmentEntity.class);
        verify(attachments).insert(captor.capture());
        var saved = captor.getValue();
        assertEquals("cbti_personality_image", saved.getBizType());
        assertEquals(7L, saved.getBizId());
        assertEquals(99L, saved.getStorageObjectId());
        assertEquals(1, saved.getIsPublic());
        assertNull(saved.getUserId());
        assertEquals(1L, saved.getCreateUser());
    }

    @Test
    void 替换时先软删除旧附件再建立新引用() {
        personality();
        var object = object();
        when(storage.store(any(), eq(1L), any())).thenReturn(object);
        var previous = new AttachmentEntity();
        previous.setId("1");
        previous.setStorageObjectId(88L);
        previous.setBizId(7L);
        when(attachments.selectWithObjectByBiz(eq("cbti_personality_image"), anyList())).thenReturn(List.of(previous));
        when(minio.getPublicImageUrl(any(), any())).thenReturn("http://host/api/f/hash.png");

        service.uploadPersonalityImage(1L, "SUDO", new MockMultipartFile("file", "sudo.png", "image/png", ImageFixtures.image("png")));

        verify(attachments).update(isNull(), any(Wrapper.class));
        verify(attachments).insert(any(AttachmentEntity.class));
    }

    @Test
    void 相同内容重复上传不重建附件() {
        personality();
        var object = object();
        when(storage.store(any(), eq(1L), any())).thenReturn(object);
        var current = new AttachmentEntity();
        current.setStorageObjectId(99L);
        current.setBizId(7L);
        when(attachments.selectWithObjectByBiz(eq("cbti_personality_image"), anyList())).thenReturn(List.of(current));
        when(minio.getPublicImageUrl(any(), any())).thenReturn("http://host/api/f/hash.png");

        service.uploadPersonalityImage(1L, "SUDO", new MockMultipartFile("file", "sudo.png", "image/png", ImageFixtures.image("png")));

        verify(attachments, never()).insert(any(AttachmentEntity.class));
        verify(attachments, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    void 初始化不覆盖已有人工上传的图片() {
        config.setCharactersDir("src/main/resources/cbti/characters");
        var entity = personality();
        when(personalities.selectList(any())).thenReturn(List.of(entity));
        var bound = new AttachmentEntity();
        bound.setBizId(7L);
        when(attachments.selectWithObjectByBiz(eq("cbti_personality_image"), anyList())).thenReturn(List.of(bound));

        service.initImages();

        verifyNoInteractions(storage);
        verify(attachments, never()).insert(any(AttachmentEntity.class));
    }

    @Test
    void 初始化按内置清单补齐缺失图片() {
        config.setCharactersDir("");
        var entity = personality();
        when(personalities.selectList(any())).thenReturn(List.of(entity));
        when(attachments.selectWithObjectByBiz(eq("cbti_personality_image"), anyList())).thenReturn(List.of());
        when(storage.store(any(), isNull(), any())).thenReturn(object());
        when(minio.getPublicImageUrl(any(), any())).thenReturn("http://host/api/f/hash.png");

        service.initImages();

        verify(storage).store(any(), isNull(), any());
        verify(attachments).insert(any(AttachmentEntity.class));
    }
}
