package top.aiolife.record.api;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import top.aiolife.config.MinioConfig;
import top.aiolife.core.util.MinioUtil;
import top.aiolife.record.mapper.IFileMapper;
import top.aiolife.record.mapper.IHonorRecordMapper;
import top.aiolife.record.pojo.entity.HonorRecordEntity;
import top.aiolife.record.pojo.vo.FileVO;
import top.aiolife.record.service.impl.FileServiceImpl;
import top.aiolife.record.service.impl.HonorRecordServiceImpl;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 在独立 H2 中执行真实 Mapper SQL 和事务，不连接用户数据库或 MinIO。 */
class HonorAttachmentPersistenceTest {
    private JdbcTemplate jdbc;
    private HonorRecordController controller;
    private MockedStatic<StpUtil> stpUtil;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:honor_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("""
                CREATE TABLE honor_record (
                    id BIGINT PRIMARY KEY, user_id BIGINT, title VARCHAR(255), description VARCHAR(255),
                    honor_date DATE, issuer VARCHAR(255), level VARCHAR(50), category_id BIGINT,
                    custom_category VARCHAR(255), tags VARCHAR(255), is_top INT, is_public INT, sort_order INT,
                    create_user BIGINT, update_user BIGINT, create_time TIMESTAMP, update_time TIMESTAMP,
                    is_deleted INT DEFAULT 0)
                """);
        jdbc.execute("""
                CREATE TABLE file (
                    id VARCHAR(32) PRIMARY KEY, biz_type VARCHAR(50), biz_id BIGINT,
                    file_name VARCHAR(255), file_size BIGINT, file_type VARCHAR(100), hash_value VARCHAR(255),
                    create_user BIGINT, update_user BIGINT, create_time TIMESTAMP, update_time TIMESTAMP,
                    is_deleted INT DEFAULT 0, is_public INT DEFAULT 0)
                """);
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(IHonorRecordMapper.class);
        configuration.addMapper(IFileMapper.class);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(source);
        factory.setConfiguration(configuration);
        var session = new SqlSessionTemplate(factory.getObject());
        var honors = new HonorRecordServiceImpl();
        var honorMapper = session.getMapper(IHonorRecordMapper.class);
        ReflectionTestUtils.setField(honors, "baseMapper", honorMapper);
        var files = new FileServiceImpl(mock(MinioUtil.class), mock(MinioConfig.class));
        ReflectionTestUtils.setField(files, "baseMapper", session.getMapper(IFileMapper.class));
        var proxy = new ProxyFactory(new HonorRecordController(honorMapper, honors, files));
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(source),
                new AnnotationTransactionAttributeSource()));
        controller = (HonorRecordController) proxy.getProxy();
        stpUtil = mockStatic(StpUtil.class);
        stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(1L);
        jdbc.update("INSERT INTO honor_record (id, user_id, title) VALUES (100, 1, '原始荣誉')");
        addFile("first", 1L, "honor_record", 100L);
        addFile("second", 1L, "honor_record", 100L);
    }

    @AfterEach
    void tearDown() {
        if (stpUtil != null) stpUtil.close();
        if (jdbc != null) jdbc.execute("DROP ALL OBJECTS");
    }

    @Test
    void update_部分删除后重新查询只返回保留附件() {
        save(List.of("second"));
        assertEquals(List.of("second"), attachmentIds());
        assertNull(bizId("first"));
        assertEquals(0, jdbc.queryForObject("SELECT is_deleted FROM file WHERE id = 'first'", Integer.class));
    }

    @Test
    void update_空列表清空所有附件且重复保存仍为空() {
        save(List.of());
        save(List.of());
        assertEquals(List.of(), attachmentIds());
        assertNull(bizId("first"));
        assertNull(bizId("second"));
    }

    @Test
    void update_未传附件字段保留原有关联() {
        save(null);
        assertEquals(List.of("first", "second"), attachmentIds());
    }

    @Test
    void update_替换时解绑旧附件并绑定新上传附件() {
        addFile("new", 1L, "honor_record", null);
        save(List.of("second", "new"));
        assertEquals(List.of("new", "second"), attachmentIds());
        assertNull(bizId("first"));
        assertEquals(100L, bizId("new"));
    }

    @Test
    void update_清空只影响当前用户当前荣誉的附件() {
        addFile("other-honor", 1L, "honor_record", 101L);
        addFile("other-user", 2L, "honor_record", 100L);
        addFile("other-type", 1L, "device", 100L);
        addFile("deleted", 1L, "honor_record", 100L);
        jdbc.update("UPDATE file SET is_deleted = 1 WHERE id = 'deleted'");
        save(List.of());
        assertNull(bizId("first"));
        assertEquals(101L, bizId("other-honor"));
        assertEquals(100L, bizId("other-user"));
        assertEquals(100L, bizId("other-type"));
        assertEquals(100L, bizId("deleted"));
    }

    @Test
    void update_无权修改荣誉时不改变附件() {
        jdbc.update("UPDATE honor_record SET user_id = 2 WHERE id = 100");
        assertThrows(IllegalArgumentException.class, () -> save(List.of()));
        assertEquals(100L, bizId("first"));
        assertEquals(100L, bizId("second"));
    }

    @Test
    void update_绑定失败时回滚荣誉修改和附件解绑() {
        addFile("bank-cover", 1L, "bank_card_cover", null);
        assertThrows(IllegalArgumentException.class, () -> save(List.of("bank-cover")));
        assertEquals(List.of("first", "second"), attachmentIds());
        assertEquals("原始荣誉", jdbc.queryForObject("SELECT title FROM honor_record WHERE id = 100", String.class));
    }

    private void save(List<String> fileIds) {
        var request = new HonorRecordEntity();
        request.setId(100L);
        request.setTitle("更新后的荣誉");
        request.setFileIds(fileIds);
        assertEquals("0", controller.updateHonorRecord(request).getRscode());
    }

    private List<String> attachmentIds() {
        return controller.getHonorRecord(100L).getData().getFiles().stream()
                .map(FileVO::getId).sorted().toList();
    }

    private Long bizId(String fileId) {
        return jdbc.queryForObject("SELECT biz_id FROM file WHERE id = ?", Long.class, fileId);
    }

    private void addFile(String id, Long userId, String bizType, Long bizId) {
        jdbc.update("INSERT INTO file (id, create_user, biz_type, biz_id) VALUES (?, ?, ?, ?)",
                id, userId, bizType, bizId);
    }
}
