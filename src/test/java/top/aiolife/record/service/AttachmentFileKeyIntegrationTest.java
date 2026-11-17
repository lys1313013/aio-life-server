package top.aiolife.record.service;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import top.aiolife.export.dataset.AttachmentDatasets;
import top.aiolife.export.mapper.ExportDatasetMapper;
import top.aiolife.record.mapper.HomeSectionMapper;
import top.aiolife.record.mapper.IAttachmentMapper;
import top.aiolife.record.pojo.entity.AttachmentEntity;
import top.aiolife.support.AttachmentDatabaseFixture;

import static org.junit.jupiter.api.Assertions.*;

/** 真实 PostgreSQL 检查接口投影不泄露内部主键，导出分页与首页图片使用同一 file_key。 */
class AttachmentFileKeyIntegrationTest extends AttachmentDatabaseFixture {
    @Test void 同字节不同附件拥有独立标识且首页和导出仅返回UUID() {
        session.getConfiguration().addMapper(HomeSectionMapper.class);
        session.getConfiguration().addMapper(ExportDatasetMapper.class);
        jdbc.execute("TRUNCATE device");
        jdbc.update("INSERT INTO storage_object(id,bucket,object_key,content_hash,file_size,content_type) VALUES(11,'fixture','objects/aa/" + "a".repeat(64) + ".png','" + "a".repeat(64) + "',68,'image/png')");
        var attachments = mapper(IAttachmentMapper.class);
        for (long business : List.of(101L, 102L)) {
            jdbc.update("INSERT INTO device(id,user_id,name,status) VALUES(?,42,'fixture','1')", business);
            var file = new AttachmentEntity();
            file.fillCreateCommonField(42L);
            file.setBizType("device_cover"); file.setBizId(business); file.setStorageObjectId(11L);
            file.setIsPublic(0);
            String key = file.getFileKey();
            file.fillCreateCommonField(42L);
            assertEquals(key, file.getFileKey());
            attachments.insert(file);
            assertNotEquals(file.getId(), key);
            assertTrue(file.getId().matches("[1-9][0-9]{0,18}"));
            assertEquals(4, UUID.fromString(key).version());
            assertEquals(key, attachments.selectWithObject(file.getId()).getFileKey());
        }
        var files = attachments.selectList(null);
        assertNotEquals(files.getFirst().getFileKey(), files.getLast().getFileKey());
        assertEquals(files.getFirst().getStorageObjectId(), files.getLast().getStorageObjectId());
        var home = mapper(HomeSectionMapper.class).activeDevices(42);
        assertEquals(2, home.size());
        assertTrue(home.stream().allMatch(item -> files.stream().anyMatch(f -> f.getFileKey().equals(item.getFileId()))));

        var dataset = new AttachmentDatasets(mapper(ExportDatasetMapper.class)).datasets().stream()
                .filter(item -> item.key().equals("files_device")).findFirst().orElseThrow();
        var first = dataset.readBatch(42, null, 1);
        assertEquals(1, first.size());
        String cursor = first.getFirst().get("id").toString();
        assertEquals(4, UUID.fromString(cursor).version());
        var second = dataset.readBatch(42, cursor, 1);
        assertEquals(1, second.size());
        assertNotEquals(cursor, second.getFirst().get("id").toString());
        assertTrue(dataset.readBatch(42, second.getFirst().get("id").toString(), 1).isEmpty());
        assertTrue(dataset.readBatch(43, null, 1).isEmpty());
    }
}
