package top.aiolife.system.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import io.minio.MinioClient;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.web.server.ResponseStatusException;
import top.aiolife.config.MinioConfig;
import top.aiolife.core.lock.StorageObjectLock;
import top.aiolife.core.util.MinioUtil;
import top.aiolife.record.enums.FileBizType;
import top.aiolife.record.mapper.IFileMapper;
import top.aiolife.record.pojo.entity.FileEntity;
import top.aiolife.record.service.impl.FileServiceImpl;
import top.aiolife.system.mapper.StorageFileReferenceMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** H2真实事务+Mapper；用线程互斥实现Redis锁测试替身，MinIO无外部副作用。 */
class StorageUploadCoordinationTest {
    JdbcTemplate jdbc;
    MinioUtil uploads;
    MinioClient deletes;
    StorageObjectLock locks;
    StorageAdminService admin;
    FileServiceImpl files;
    ExecutorService executor;
    CountDownLatch written, finish;
    AtomicReference<String> key;
    boolean failSave;
    final Map<String, ReentrantLock> mutexes = new ConcurrentHashMap<>();

    @BeforeEach void setup() throws Exception {
        written = new CountDownLatch(1); finish = new CountDownLatch(1); key = new AtomicReference<>();
        executor = Executors.newSingleThreadExecutor();
        var ds = new JdbcDataSource(); ds.setURL("jdbc:h2:mem:upload_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE file(id VARCHAR(32) PRIMARY KEY, file_name VARCHAR(1024), file_size BIGINT, file_type VARCHAR(100), hash_value VARCHAR(255), biz_type VARCHAR(50), biz_id BIGINT, is_public INT, create_user BIGINT, update_user BIGINT, create_time TIMESTAMP, update_time TIMESTAMP, is_deleted INT DEFAULT 0)");
        jdbc.execute("CREATE TABLE cbti_personality(id BIGINT, image_object VARCHAR(1024), is_deleted INT)");
        var cfg = new MybatisConfiguration(); cfg.setMapUnderscoreToCamelCase(true);
        cfg.addMapper(IFileMapper.class); cfg.addMapper(StorageFileReferenceMapper.class);
        var factory = new MybatisSqlSessionFactoryBean(); factory.setDataSource(ds); factory.setConfiguration(cfg);
        var session = new SqlSessionTemplate(factory.getObject());
        var redis = mock(RedissonClient.class);
        when(redis.getLock(anyString())).thenAnswer(call -> {
            var mutex = mutexes.computeIfAbsent(call.getArgument(0), ignored -> new ReentrantLock());
            var lock = mock(RLock.class);
            when(lock.tryLock()).thenAnswer(ignored -> mutex.tryLock());
            when(lock.isHeldByCurrentThread()).thenAnswer(ignored -> mutex.isHeldByCurrentThread());
            doAnswer(ignored -> {mutex.unlock(); return null;}).when(lock).unlock();
            return lock;
        });
        locks = new StorageObjectLock(redis);
        uploads = mock(MinioUtil.class); deletes = mock(MinioClient.class);
        doAnswer(call -> { key.set(call.getArgument(2)); return null; }).when(uploads).uploadFile(anyString(), any(), anyString());
        var config = new MinioConfig(); config.setBucketName("business");
        var target = new FileServiceImpl(uploads, config, locks, new top.aiolife.record.service.DoubanCoverUrlPolicy("doubanio.com")) {
            @Override public boolean save(FileEntity entity) {
                boolean saved = super.save(entity);
                written.countDown();
                try { if (!finish.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("test timeout"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                if (failSave) throw new IllegalStateException("simulated persistence failure");
                return saved;
            }
        };
        ReflectionTestUtils.setField(target, "baseMapper", session.getMapper(IFileMapper.class));
        var proxy = new ProxyFactory(target); proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(ds), new AnnotationTransactionAttributeSource()));
        files = (FileServiceImpl) proxy.getProxy();
        admin = new StorageAdminService(config, mock(StorageListClient.class), deletes,
                new StorageFileReferenceGuard(session.getMapper(StorageFileReferenceMapper.class), new top.aiolife.config.CbtiConfig()), locks);
    }

    @AfterEach void close() throws Exception {
        finish.countDown(); executor.shutdown(); assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        jdbc.execute("DROP ALL OBJECTS");
    }

    Future<?> upload() {
        return executor.submit(() -> {
            try (var login = mockStatic(StpUtil.class)) {
                login.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
                files.upload(new MockMultipartFile("file", "image.png", "image/png", new byte[]{1}), FileBizType.AVATAR);
            }
        });
    }

    @Test void 未提交上传阻止管理员删除且提交后引用继续保护() throws Exception {
        var result = upload(); assertTrue(written.await(10, TimeUnit.SECONDS));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM file", Integer.class));
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> admin.delete(key.get())).getStatusCode().value());
        verifyNoInteractions(deletes);
        finish.countDown(); result.get(10, TimeUnit.SECONDS);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM file", Integer.class));
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> admin.delete(key.get())).getStatusCode().value());
        verifyNoInteractions(deletes);
        assertTrue(mutexes.values().stream().noneMatch(ReentrantLock::isLocked));
    }

    @Test void 回滚清理完成前保持对象锁且最终释放() throws Exception {
        failSave = true;
        doAnswer(call -> {
            assertTrue(mutexes.values().stream().anyMatch(ReentrantLock::isHeldByCurrentThread), "回滚清理时仍须持锁");
            return null;
        }).when(uploads).removeObject(anyString(), anyString());
        var result = upload(); assertTrue(written.await(10, TimeUnit.SECONDS));
        finish.countDown(); assertThrows(ExecutionException.class, () -> result.get(10, TimeUnit.SECONDS));
        verify(uploads).removeObject("business", key.get());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM file", Integer.class));
        assertTrue(mutexes.values().stream().noneMatch(ReentrantLock::isLocked));
        admin.delete(key.get()); verify(deletes).removeObject(any());
    }

    @Test void 未经事务代理的上传在写对象前被拒绝() {
        assertThrows(IllegalStateException.class, () -> locks.holdUntilTransactionCompletion("business", "image.png"));
        assertTrue(mutexes.isEmpty());
    }

    @Test void URL上传同样保持锁直到文件事务提交() throws Exception {
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/cover.png", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "image/png");
            exchange.sendResponseHeaders(200, 1); exchange.getResponseBody().write(1); exchange.close();
        });
        server.start();
        doAnswer(call -> { key.set(call.getArgument(1)); return null; }).when(uploads).putObject(anyString(), anyString(), any(), anyLong(), anyString());
        try {
            var result = executor.submit(() -> {
                try (var login = mockStatic(StpUtil.class)) {
                    login.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
                    files.uploadFromUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/cover.png", FileBizType.AVATAR);
                }
            });
            assertTrue(written.await(10, TimeUnit.SECONDS));
            assertEquals(409, assertThrows(ResponseStatusException.class, () -> admin.delete(key.get())).getStatusCode().value());
            finish.countDown(); result.get(10, TimeUnit.SECONDS);
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM file", Integer.class));
            verifyNoInteractions(deletes);
        } finally { finish.countDown(); server.stop(0); }
    }

    @Test void CBTI直接上传的对象与人格引用更新也在共享锁内() throws Exception {
        var cbti = new top.aiolife.config.CbtiConfig();
        var config = new MinioConfig(); config.setBucketName("business");
        var mapper = mock(top.aiolife.record.mapper.ICbtiPersonalityMapper.class);
        var entity = new top.aiolife.record.pojo.entity.CbtiPersonalityEntity(); entity.setId(7L); entity.setCode("TEST");
        when(mapper.selectOne(any())).thenReturn(entity);
        jdbc.update("INSERT INTO cbti_personality VALUES(7, 'old.png', 0)");
        when(mapper.updateById(any(top.aiolife.record.pojo.entity.CbtiPersonalityEntity.class))).thenAnswer(call ->
                jdbc.update("UPDATE cbti_personality SET image_object=? WHERE id=7", call.getArgument(0, top.aiolife.record.pojo.entity.CbtiPersonalityEntity.class).getImageObject()));
        doAnswer(call -> {
            key.set(call.getArgument(2)); written.countDown();
            if (!finish.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("test timeout");
            return key.get();
        }).when(uploads).uploadFile(anyString(), any(), anyString());
        var controller = new top.aiolife.record.api.CbtiAdminController(mapper, new com.fasterxml.jackson.databind.ObjectMapper(),
                uploads, config, cbti, mock(top.aiolife.record.config.CbtiImageInitUtil.class), locks);
        var result = executor.submit(() -> {
            try (var login = mockStatic(StpUtil.class)) {
                login.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
                return controller.uploadImage("TEST", new MockMultipartFile("file", "test.png", "image/png", new byte[]{1}));
            }
        });
        assertTrue(written.await(10, TimeUnit.SECONDS));
        var busy = assertThrows(ResponseStatusException.class, () -> admin.delete(key.get()));
        assertTrue(busy.getReason().contains("处理中"));
        finish.countDown(); assertEquals("0", result.get(10, TimeUnit.SECONDS).getRscode());
        var referenced = assertThrows(ResponseStatusException.class, () -> admin.delete(key.get()));
        assertTrue(referenced.getReason().contains("CBTI"));
        verifyNoInteractions(deletes);
    }
}
