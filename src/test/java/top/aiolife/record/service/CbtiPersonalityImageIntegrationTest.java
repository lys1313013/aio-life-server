package top.aiolife.record.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import top.aiolife.config.CbtiConfig;
import top.aiolife.config.MinioConfig;
import top.aiolife.core.lock.StorageObjectLock;
import top.aiolife.core.util.MinioUtil;
import top.aiolife.record.api.CbtiAdminController;
import top.aiolife.record.enums.AttachmentBizType;
import top.aiolife.record.mapper.IAttachmentMapper;
import top.aiolife.record.mapper.ICbtiPersonalityMapper;
import top.aiolife.record.mapper.ICbtiResultMapper;
import top.aiolife.record.mapper.PublicImageMapper;
import top.aiolife.record.pojo.entity.AttachmentEntity;
import top.aiolife.record.service.impl.CbtiServiceImpl;
import top.aiolife.support.AttachmentDatabaseFixture;
import top.aiolife.system.mapper.StorageObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static top.aiolife.support.ImageFixtures.image;

/** 真实 PostgreSQL、Mapper 和事务，隔离对象存储及 Redis。 */
class CbtiPersonalityImageIntegrationTest extends AttachmentDatabaseFixture {
    private static final String IMAGE_TYPE = AttachmentBizType.CBTI_PERSONALITY.getBizType();
    private static final long SUDO = 1L;
    private static final long JAVA = 2L;
    private static final long ADMIN = 42L;
    private CbtiAdminController controller;

    @BeforeEach
    void 设置人格夹具() {
        jdbc.execute("TRUNCATE cbti_personality");
        jdbc.update("INSERT INTO cbti_personality(id,code,name) VALUES (?,'SUDO','SUDO'), (?,'JAVA','JAVA')",
                SUDO, JAVA);
        var minio = new MinioUtil();
        ReflectionTestUtils.setField(minio, "serveBaseUrl", "http://fixture.test/api");
        var storage = proxy(new VideoCoverStorage(mapper(StorageObjectMapper.class), mock(StorageObjectLock.class),
                mock(MinioUtil.class), new MinioConfig()));
        var service = proxy(new CbtiServiceImpl(mapper(ICbtiPersonalityMapper.class), mapper(ICbtiResultMapper.class),
                mapper(IAttachmentMapper.class), storage, new ObjectMapper(), minio, new CbtiConfig()));
        controller = new CbtiAdminController(service);
    }

    @AfterEach
    void 清理人格夹具() {
        jdbc.update("DELETE FROM cbti_personality WHERE id IN (?, ?)", SUDO, JAVA);
    }

    @Test
    void 上传以系统归属保存并可公开读取() {
        String url = upload("png", "image/png");

        var uploaded = onlyImage(SUDO);
        assertEquals(ADMIN, uploaded.getCreateUser());
        assertEquals("http://fixture.test/api/f/" + uploaded.getHashValue() + ".png", url);
    }

    @Test
    void 初始化补齐内置图且重复执行保留已有图片() {
        upload("png", "image/png");
        String customKey = onlyImage(SUDO).getFileKey();

        assertTrue(controller.initImages().getData());
        var initialized = onlyImage(JAVA);
        assertNull(initialized.getCreateUser());
        assertEquals(customKey, onlyImage(SUDO).getFileKey());

        assertTrue(controller.initImages().getData());
        assertEquals(initialized.getFileKey(), onlyImage(JAVA).getFileKey());
    }

    @Test
    void 换图软删除旧附件并撤销其公开访问() {
        upload("png", "image/png");
        var old = onlyImage(SUDO);

        String replacementUrl = upload("jpg", "image/jpeg");

        var replacement = onlyImage(SUDO);
        assertNotEquals(old.getFileKey(), replacement.getFileKey());
        assertEquals("http://fixture.test/api/f/" + replacement.getHashValue() + ".jpg", replacementUrl);
        assertNull(mapper(PublicImageMapper.class).selectPublished(old.getHashValue()));
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM attachment WHERE file_key=?::uuid AND is_deleted=1 AND is_public=0",
                Integer.class, old.getFileKey()));
    }

    @Test
    void 数据库拒绝重复有效图和错误的附件归属() {
        upload("png", "image/png");
        var uploaded = onlyImage(SUDO);

        assertThrows(DuplicateKeyException.class,
                () -> insertAttachment(IMAGE_TYPE, SUDO, null, uploaded.getStorageObjectId()));
        assertThrows(DataIntegrityViolationException.class,
                () -> insertAttachment(IMAGE_TYPE, JAVA, ADMIN, uploaded.getStorageObjectId()));
        assertThrows(DataIntegrityViolationException.class,
                () -> insertAttachment("movie_cover", SUDO, null, uploaded.getStorageObjectId()));
        insertAttachment("bank_card_template_face", SUDO, null, uploaded.getStorageObjectId());
    }

    private String upload(String format, String contentType) {
        var file = new MockMultipartFile("file", "fixture." + format, contentType, image(format));
        return (String) controller.uploadImage(Long.toString(ADMIN), "SUDO", file).getData().get("imageUrl");
    }

    private AttachmentEntity onlyImage(long personalityId) {
        var bound = mapper(IAttachmentMapper.class).selectWithObjectByBiz(IMAGE_TYPE, List.of(personalityId));
        assertEquals(1, bound.size());
        var attachment = bound.getFirst();
        assertNull(attachment.getUserId());
        assertEquals(1, attachment.getIsPublic());
        var published = mapper(PublicImageMapper.class).selectPublished(attachment.getHashValue());
        assertNotNull(published);
        assertEquals(attachment.getStorageObjectId(), published.getId());
        return attachment;
    }

    private void insertAttachment(String type, long bizId, Long owner, long objectId) {
        jdbc.update("""
                INSERT INTO attachment(id,file_key,biz_type,biz_id,user_id,storage_object_id)
                VALUES (7,gen_random_uuid(),?,?,?,?)
                """, type, bizId, owner, objectId);
    }
}
