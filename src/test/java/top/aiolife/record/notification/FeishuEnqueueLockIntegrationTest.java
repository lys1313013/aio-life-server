package top.aiolife.record.notification;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import top.aiolife.core.lock.DistributedLockExecutor;
import top.aiolife.record.mapper.*;
import top.aiolife.record.pojo.entity.NotificationChannelConfigEntity;
import top.aiolife.record.service.IUserBindService;
import top.aiolife.record.service.impl.FeishuNotificationServiceImpl;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 只允许指向专用临时数据库；测试会重建 notification_delivery，不发送真实飞书消息。 */
@EnabledIfEnvironmentVariable(named = "AIO_LOCK_TEST_MYSQL_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AIO_LOCK_TEST_REDIS_PORT", matches = ".+")
class FeishuEnqueueLockIntegrationTest {
    private static RedissonClient first;
    private static RedissonClient second;
    private static NotificationDeliveryMapper deliveries;
    private static JdbcTemplate jdbc;
    private static TransactionTemplate transactions;
    private static DataSourceTransactionManager transactionManager;
    private static FeishuNotificationServiceImpl firstService;
    private static FeishuNotificationServiceImpl secondService;

    @BeforeAll
    static void setup() throws Exception {
        var source = new DriverManagerDataSource(System.getenv("AIO_LOCK_TEST_MYSQL_URL"), "root", "notification_test");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("DROP TABLE IF EXISTS notification_delivery");
        String init = Files.readString(Path.of("sql/1_init_table/2026-08-18_init_all_tables.sql"));
        int start = init.indexOf("CREATE TABLE IF NOT EXISTS `notification_delivery`");
        jdbc.execute(init.substring(start, init.indexOf(';', start)));
        var config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        config.addMapper(NotificationDeliveryMapper.class);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(source);
        factory.setConfiguration(config);
        deliveries = new SqlSessionTemplate(factory.getObject()).getMapper(NotificationDeliveryMapper.class);
        transactionManager = new DataSourceTransactionManager(source);
        transactions = new TransactionTemplate(transactionManager);
        transactions.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        Config redisConfig = new Config();
        redisConfig.useSingleServer().setAddress("redis://127.0.0.1:" + System.getenv("AIO_LOCK_TEST_REDIS_PORT"))
                .setConnectionMinimumIdleSize(1).setConnectionPoolSize(4);
        first = Redisson.create(redisConfig);
        second = Redisson.create(redisConfig);
        firstService = service(first);
        secondService = service(second);
    }

    private static FeishuNotificationServiceImpl service(RedissonClient client) {
        var configs = mock(NotificationChannelConfigMapper.class);
        var preferences = mock(NotificationPreferenceMapper.class);
        var crypto = mock(NotificationCryptoService.class);
        var enabled = new NotificationChannelConfigEntity();
        enabled.setEnabled(1);
        when(configs.selectOne(any())).thenReturn(enabled);
        when(crypto.encrypt(any())).thenReturn("encrypted-payload");
        return new FeishuNotificationServiceImpl(configs, preferences, deliveries, crypto,
                mock(FeishuAppClient.class), new ObjectMapper(), mock(IUserBindService.class),
                new DistributedLockExecutor(client), transactionManager);
    }

    @AfterAll
    static void cleanup() {
        if (first != null) first.shutdown();
        if (second != null) second.shutdown();
    }

    @BeforeEach
    void clear() {
        jdbc.update("DELETE FROM notification_delivery");
    }

    @Test
    void sendIfEnabled_两个实例并发和晚到请求只入队一次() throws Exception {
        var request = request();
        RedissonNotificationIntegrationTest.runConcurrent(16,
                index -> (index % 2 == 0 ? firstService : secondService).sendIfEnabled(request));
        secondService.sendIfEnabled(request);
        assertEquals(1, count());
    }

    @Test
    void sendIfEnabled_直到外层事务提交才释放锁() throws Exception {
        verifyTransaction(false);
    }

    @Test
    void sendIfEnabled_回滚后释放锁且允许重新入队() throws Exception {
        verifyTransaction(true);
    }

    private void verifyTransaction(boolean rollback) throws Exception {
        var request = request();
        var inserted = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        try (var pool = Executors.newSingleThreadExecutor()) {
            var worker = pool.submit(() -> transactions.executeWithoutResult(status -> {
                firstService.sendIfEnabled(request);
                inserted.countDown();
                RedissonNotificationIntegrationTest.await(finish);
                if (rollback) status.setRollbackOnly();
            }));
            try {
                assertTrue(inserted.await(5, TimeUnit.SECONDS));
                assertTrue(second.getLock("notification:feishu:enqueue:lock:7:" + request.dedupKey()).isLocked());
                secondService.sendIfEnabled(request);
                assertEquals(0, count()); // 未提交记录不可见，第二个实例必须跳过。
            } finally {
                finish.countDown();
            }
            worker.get(5, TimeUnit.SECONDS);
            assertEquals(rollback ? 0 : 1, count());
            secondService.sendIfEnabled(request);
            assertEquals(1, count());
        }
    }

    @Test
    void sendIfEnabled_已有事务旧快照不能导致重复入队() throws Exception {
        var request = request();
        var snapshotReady = new CountDownLatch(1);
        var committed = new CountDownLatch(1);
        try (var pool = Executors.newSingleThreadExecutor()) {
            var worker = pool.submit(() -> transactions.executeWithoutResult(status -> {
                assertEquals(0L, deliveries.selectCount(null)); // 建立 REPEATABLE READ 旧快照。
                snapshotReady.countDown();
                RedissonNotificationIntegrationTest.await(committed);
                secondService.sendIfEnabled(request);
            }));
            try {
                assertTrue(snapshotReady.await(5, TimeUnit.SECONDS));
                firstService.sendIfEnabled(request);
                assertEquals(1, count());
            } finally {
                committed.countDown();
            }
            worker.get(5, TimeUnit.SECONDS);
            assertEquals(1, count());
        }
    }

    @Test
    void sendIfEnabled_同一事务重复事件只插入一次() {
        var request = request();
        transactions.executeWithoutResult(status -> {
            firstService.sendIfEnabled(request);
            firstService.sendIfEnabled(request);
            assertEquals(1L, deliveries.selectCount(null));
        });
        assertEquals(1, count());
        secondService.sendIfEnabled(request);
        assertEquals(1, count());
    }

    @Test
    void sendIfEnabled_不同事件并发事务不互相阻塞且都能提交() throws Exception {
        var firstRequest = request();
        var secondRequest = request();
        var bothInserted = new CountDownLatch(2);
        RedissonNotificationIntegrationTest.runConcurrent(2, index -> transactions.executeWithoutResult(status -> {
            // 两个事务都从空表快照开始，模拟两条独立反馈同时创建通知。
            assertEquals(0L, deliveries.selectCount(null));
            (index == 0 ? firstService : secondService)
                    .sendIfEnabled(index == 0 ? firstRequest : secondRequest);
            // 每个事务必须能读到自己的入队；否则不能把被吞掉的 SQL 异常误判为成功。
            assertEquals(1L, deliveries.selectCount(null));
            bothInserted.countDown();
            // 两条入队都完成前不提交，数据库范围锁会导致超时或死锁。
            RedissonNotificationIntegrationTest.await(bothInserted);
        }));
        assertEquals(2, count());
    }

    private NotificationRequest request() {
        return new NotificationRequest(7L, "LEETCODE_REMINDER", "title", "text", null, UUID.randomUUID().toString());
    }

    private int count() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM notification_delivery", Integer.class);
    }
}
