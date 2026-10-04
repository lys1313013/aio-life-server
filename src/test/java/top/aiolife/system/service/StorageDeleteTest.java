package top.aiolife.system.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.mockito.ArgumentCaptor;
import org.mybatis.spring.SqlSessionTemplate;
import top.aiolife.config.CbtiConfig;
import top.aiolife.core.lock.StorageObjectLock;
import top.aiolife.system.mapper.StorageFileReferenceMapper;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import top.aiolife.config.MinioConfig;
import top.aiolife.system.mapper.StorageObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 真正执行关联 SQL；MinIO 使用 Mock，测试不会删除用户的文件。 */
class StorageDeleteTest {
    JdbcTemplate jdbc;
    MinioClient minio;
    StorageAdminService service;

    @BeforeEach
    void setup() throws Exception {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE file(id VARCHAR(32), file_name VARCHAR(1024), create_user BIGINT, biz_type VARCHAR(50), biz_id BIGINT, is_deleted INT)");
        jdbc.execute("CREATE TABLE cbti_personality(id BIGINT, image_object VARCHAR(1024), is_deleted INT)");
        var config = new MinioConfig();
        config.setBucketName("business");
        minio = mock(MinioClient.class);
        var mybatis = new MybatisConfiguration();
        mybatis.addMapper(StorageFileReferenceMapper.class);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(ds);
        factory.setConfiguration(mybatis);
        var session = new SqlSessionTemplate(factory.getObject());
        service = new StorageAdminService(config, mock(StorageListClient.class), minio,
                new StorageFileReferenceGuard(session.getMapper(StorageFileReferenceMapper.class), new CbtiConfig(), mock(StorageObjectMapper.class)), mock(StorageObjectLock.class));
    }

    void record(String name, Long owner, String type, Long bizId, int deleted) {
        jdbc.update("INSERT INTO file VALUES(?,?,?,?,?,?)", "file-reference", name, owner, type, bizId, deleted);
    }

    @ParameterizedTest
    @CsvSource({
            "7/honor/a.jpg,7/honor/a.jpg,honor_record",
            "https://old.example/api/file/preview/business/7/honor/a.jpg,7/honor/a.jpg,honor_record",
            "/business/7/honor/a.jpg,7/honor/a.jpg,honor_record",
            "business/7/honor/a.jpg,7/honor/a.jpg,honor_record",
            "a.jpg,7/honor/a.jpg,honor_record",
            "a.jpg,7/honor_record/a.jpg,honor_record",
            "a.jpg,7/honor-record/a.jpg,honor_record",
            "a.jpg,7/wardrobe/a.jpg,wardrobe_item",
            "a.jpg,7/bank-card/a.jpg,bank_card_cover",
            "https://old.example/business/a.jpg,7/honor/a.jpg,honor_record"
    })
    void 所有现有文件路径格式有关联时禁止删除(String name, String key, String type) {
        record(name, 7L, type, 99L, 0);
        var error = assertThrows(ResponseStatusException.class, () -> service.delete(key));
        assertEquals(409, error.getStatusCode().value());
        assertTrue(error.getReason().contains("file-reference"));
        assertTrue(error.getReason().contains("99"));
        verifyNoInteractions(minio);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM file", Integer.class));
    }

    @Test
    void 未绑定业务和已软删除记录仍然禁止删除() {
        record("中文 +#%_!.png", null, "avatar", null, 1);
        var error = assertThrows(ResponseStatusException.class, () -> service.delete("中文 +#%_!.png"));
        assertEquals(409, error.getStatusCode().value());
        assertTrue(error.getReason().contains("未绑定"));
        assertTrue(error.getReason().contains("已软删除"));
        verifyNoInteractions(minio);
    }

    @Test
    void URL文件名通配符按原值匹配且不存在关联时仅删除指定对象() throws Exception {
        record("https://old.example/business/中文 +#%_!.png", 7L, "avatar", 99L, 0);
        assertThrows(ResponseStatusException.class, () -> service.delete("中文 +#%_!.png"));
        verifyNoInteractions(minio);
        service.delete("中文 +#OTHER.png");
        var args = ArgumentCaptor.forClass(RemoveObjectArgs.class);
        verify(minio).removeObject(args.capture());
        assertEquals("business", args.getValue().bucket());
        assertEquals("中文 +#OTHER.png", args.getValue().object());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM file", Integer.class));
    }

    @Test
    void 不会因同名但不同属主业务或桶的文件误判关联() throws Exception {
        record("a.jpg", 8L, "honor_record", 99L, 0);
        service.delete("7/honor/a.jpg");
        jdbc.update("DELETE FROM file");
        record("a.jpg", 7L, "movie", 99L, 0);
        service.delete("7/honor/a.jpg");
        jdbc.update("DELETE FROM file");
        record("https://old.example/other/7/honor/a.jpg", 7L, "honor_record", 99L, 0);
        service.delete("7/honor/a.jpg");
        verify(minio, times(3)).removeObject(any());
    }

    @Test
    void 查库失败时拒绝继续删除() {
        jdbc.execute("DROP TABLE file");
        assertThrows(DataAccessException.class, () -> service.delete("orphan.jpg"));
        verifyNoInteractions(minio);
    }

    @Test
    void 拒绝目录空对象名及超长对象名() {
        for (String key : new String[]{"folder/", "", " ", "中".repeat(342)}) {
            var error = assertThrows(ResponseStatusException.class, () -> service.delete(key));
            assertEquals(400, error.getStatusCode().value());
        }
        verifyNoInteractions(minio);
    }

    @Test
    void 删除前重新检查刚新增的关联() throws Exception {
        service.delete("orphan.jpg");
        clearInvocations(minio);
        record("orphan.jpg", 7L, "avatar", null, 0);
        assertThrows(ResponseStatusException.class, () -> service.delete("orphan.jpg"));
        verifyNoInteractions(minio);
    }
    @Test
    void CBTI图片含软删除引用均不可清理() {
        jdbc.update("INSERT INTO cbti_personality VALUES(7, 'images/cbti/test.png', 1)");
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> service.delete("images/cbti/test.png")).getStatusCode().value());
        verifyNoInteractions(minio);
    }
}
