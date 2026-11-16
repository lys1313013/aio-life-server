package top.aiolife.record.service;

import org.junit.jupiter.api.*;
import top.aiolife.core.lock.StorageObjectLock;
import top.aiolife.core.util.MinioUtil;
import top.aiolife.record.mapper.AttachmentCollectionMapper;
import top.aiolife.support.AttachmentDatabaseFixture;
import top.aiolife.system.mapper.StorageObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AttachmentCollectionServiceTest extends AttachmentDatabaseFixture {
    private MinioUtil minio;
    private AttachmentCollectionService collection;
    @BeforeEach void setup() {
        minio=mock(MinioUtil.class);
        collection=proxy(new AttachmentCollectionService(mapper(AttachmentCollectionMapper.class),mapper(StorageObjectMapper.class),mock(StorageObjectLock.class),minio));
        jdbc.update("INSERT INTO storage_object(id,bucket,object_key,content_hash,file_size,content_type,create_time) VALUES(11,'fixture',?, ?,68,'image/png',CURRENT_TIMESTAMP-INTERVAL '8 days')","objects/aa/"+"a".repeat(64)+".png","a".repeat(64));
    }
    @Test void 待绑定附件过期后才允许回收且字节只在提交后删除() throws Exception {
        jdbc.update("INSERT INTO attachment(file_key,id,storage_object_id,user_id,biz_type,update_time) VALUES(gen_random_uuid(),12,11,42,'avatar',CURRENT_TIMESTAMP-INTERVAL '8 days')");
        collection.collect(11);verifyNoInteractions(minio);
        collection.expire();assertEquals(1,jdbc.queryForObject("SELECT is_deleted FROM attachment",Integer.class));
        transaction.executeWithoutResult(t->{try{collection.collect(11);}catch(Exception e){throw new RuntimeException(e);}verifyNoInteractions(minio);});
        assertNull(mapper(StorageObjectMapper.class).selectById(11));verify(minio).removeObject(eq("fixture"),anyString());
    }
    @Test void 数据库回滚保留对象索引和字节() {
        transaction.executeWithoutResult(t->{try{collection.collect(11);}catch(Exception e){throw new RuntimeException(e);}t.setRollbackOnly();});
        assertNotNull(mapper(StorageObjectMapper.class).selectById(11));verifyNoInteractions(minio);
    }
    @Test void 无附件的成功来源也永久保护对象() throws Exception {
        jdbc.update("INSERT INTO external_resource(id,platform,resource_type,external_key,source_url,state,storage_object_id) VALUES(12,'douban','book_cover','p1','https://example.test','READY',11)");
        collection.collect(11);assertNotNull(mapper(StorageObjectMapper.class).selectById(11));verifyNoInteractions(minio);
    }
    @Test void 存储删除失败不会恢复可绑定的悬空对象行() throws Exception {
        doThrow(new IllegalStateException("fixture")).when(minio).removeObject(anyString(),anyString());
        collection.collect(11);assertNull(mapper(StorageObjectMapper.class).selectById(11));
    }
}
