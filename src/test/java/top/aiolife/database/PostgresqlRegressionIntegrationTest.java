package top.aiolife.database;

import cn.dev33.satoken.stp.StpUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import top.aiolife.bankcard.mapper.BankCardCoverTemplateMapper;
import top.aiolife.bankcard.mapper.BankCardDictionaryMapper;
import top.aiolife.bankcard.mapper.BankCardFileMapper;
import top.aiolife.bankcard.mapper.BankCardMapper;
import top.aiolife.bankcard.mapper.BankCardTagMapper;
import top.aiolife.bankcard.mapper.BankCardTagRelMapper;
import top.aiolife.bankcard.pojo.req.BankCardReq;
import top.aiolife.bankcard.service.BankCardCoverTemplateService;
import top.aiolife.bankcard.service.BankCardCrypto;
import top.aiolife.bankcard.service.BankCardDictionaryGuard;
import top.aiolife.bankcard.service.BankCardService;
import top.aiolife.config.MinioConfig;
import top.aiolife.config.MybatisPlusConfig;
import top.aiolife.core.lock.StorageObjectLock;
import top.aiolife.core.mybatis.NumericBooleanTypeHandler;
import top.aiolife.core.query.CommonQuery;
import top.aiolife.core.query.QueryOrder;
import top.aiolife.core.redis.RedisUtil;
import top.aiolife.core.util.MinioUtil;
import top.aiolife.record.api.DeviceController;
import top.aiolife.record.api.ExpController;
import top.aiolife.record.api.IncomeController;
import top.aiolife.record.api.MemoController;
import top.aiolife.record.mapper.IBVideoMapper;
import top.aiolife.record.mapper.IDeviceMapper;
import top.aiolife.record.mapper.IExpenseMapper;
import top.aiolife.record.mapper.IFileMapper;
import top.aiolife.record.mapper.IIncomeMapper;
import top.aiolife.record.mapper.IMemoMapper;
import top.aiolife.record.mapper.ITaskMapper;
import top.aiolife.record.mapper.ITimeRecordMapper;
import top.aiolife.record.mapper.ITimeTrackerCategoryMapper;
import top.aiolife.record.mapper.ImageImportTaskMapper;
import top.aiolife.record.mapper.UserBindMapper;
import top.aiolife.record.pojo.entity.BVideoEntity;
import top.aiolife.record.pojo.entity.TaskEntity;
import top.aiolife.record.pojo.entity.TimeTrackerCategoryEntity;
import top.aiolife.record.pojo.query.DeviceQuery;
import top.aiolife.record.pojo.query.ExpenseQuery;
import top.aiolife.record.pojo.query.IncomeQuery;
import top.aiolife.record.pojo.req.ExpenseUpdateReq;
import top.aiolife.record.pojo.req.IncomeUpdateReq;
import top.aiolife.record.pojo.req.MemoUpdateReq;
import top.aiolife.record.service.DeviceExpenseService;
import top.aiolife.record.service.DoubanCoverUrlPolicy;
import top.aiolife.record.service.IExpenseService;
import top.aiolife.record.service.IMailService;
import top.aiolife.record.service.IMemoService;
import top.aiolife.record.service.TimeTrackerCategoryCache;
import top.aiolife.record.service.UserDictDataService;
import top.aiolife.record.service.VideoCoverDownloader;
import top.aiolife.record.service.VideoCoverService;
import top.aiolife.record.service.VideoCoverStorage;
import top.aiolife.record.service.impl.FileServiceImpl;
import top.aiolife.record.service.impl.TimeTrackerCategoryServiceImpl;
import top.aiolife.record.service.impl.WereadServiceImpl;
import top.aiolife.record.weread.WereadClient;
import top.aiolife.record.weread.WereadReadCache;
import top.aiolife.scheduling.mapper.ScheduledJobMapper;
import top.aiolife.scheduling.pojo.entity.ScheduledJobEntity;
import top.aiolife.sso.mapper.ApiKeyMapper;
import top.aiolife.sso.mapper.LoginLogMapper;
import top.aiolife.sso.mapper.UserMapper;
import top.aiolife.sso.mapper.UserSecondaryLockMenuMapper;
import top.aiolife.sso.pojo.entity.UserEntity;
import top.aiolife.sso.service.LoginSessionService;
import top.aiolife.sso.service.SecondaryLockMenuResolver;
import top.aiolife.sso.service.UserAvatarFileService;
import top.aiolife.sso.service.impl.UserServiceImpl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 自建 PostgreSQL 18、生产 DDL、真实 Mapper 与分页插件；不读取本机/业务库连接。 */
class PostgresqlRegressionIntegrationTest {
    private static final GenericContainer<?> POSTGRES = new GenericContainer<>("postgres:18-alpine")
            .withEnv("POSTGRES_PASSWORD", "isolated-regression")
            .withEnv("POSTGRES_DB", "fixture").withExposedPorts(5432)
            .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\n", 2))
            .withStartupTimeout(Duration.ofMinutes(2));
    private static final LocalDateTime OLD_TIME = LocalDateTime.of(2000, 1, 1, 0, 0);
    private static JdbcTemplate jdbc;
    private static SqlSessionTemplate session;
    private static DataSourceTransactionManager transactions;
    private TransactionStatus transaction;

    @BeforeAll static void 数据库初始化() throws Exception {
        POSTGRES.start();
        var source = new PGSimpleDataSource();
        source.setURL("jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432)
                + "/fixture?stringtype=unspecified");
        source.setUser("postgres"); source.setPassword("isolated-regression");
        jdbc = new JdbcTemplate(source);
        try (var paths = Files.list(Path.of("sql/migrations"))) {
            for (var path : paths
                    .filter(p -> p.getFileName().toString().matches("V\\d.*\\.sql"))
                    .filter(p -> !p.getFileName().toString().startsWith("V20261110_02_"))
                    .sorted()
                    .toList()) {
                jdbc.execute(Files.readString(path));
            }
        }
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.getTypeHandlerRegistry().register(NumericBooleanTypeHandler.class);
        for (var mapper : List.of(BankCardFileMapper.class, BankCardMapper.class, BankCardDictionaryMapper.class,
                BankCardTagMapper.class, BankCardTagRelMapper.class, BankCardCoverTemplateMapper.class,
                UserBindMapper.class, UserMapper.class, IDeviceMapper.class, IIncomeMapper.class, IExpenseMapper.class,
                IMemoMapper.class, ITaskMapper.class,
                IBVideoMapper.class, IFileMapper.class, ImageImportTaskMapper.class, ITimeTrackerCategoryMapper.class,
                ScheduledJobMapper.class)) configuration.addMapper(mapper);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(source); factory.setConfiguration(configuration);
        factory.setPlugins(new MybatisPlusConfig().mybatisPlusInterceptor());
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/*.xml"));
        session = new SqlSessionTemplate(factory.getObject());
        transactions = new DataSourceTransactionManager(source);
    }

    @BeforeEach void 开启隔离事务() {
        transaction = transactions.getTransaction(new DefaultTransactionDefinition());
        jdbc.update("INSERT INTO sys_user(id,username,nickname,update_time) VALUES (42,'fixture','before',?)", OLD_TIME);
    }
    @AfterEach void 回滚夹具() { transactions.rollback(transaction); }
    @AfterAll static void 清理数据库() { POSTGRES.stop(); }

    private <T> T mapper(Class<T> type) { return session.getMapper(type); }
    private LocalDateTime updated(String table, long id) {
        return jdbc.queryForObject("SELECT update_time FROM " + table + " WHERE id=?", LocalDateTime.class, id);
    }
    private IncomeController incomeController() {
        return new IncomeController(mapper(IIncomeMapper.class), mock(UserDictDataService.class));
    }
    private ExpController expenseController() {
        return new ExpController(mock(UserDictDataService.class), mock(IExpenseService.class),
                mock(DeviceExpenseService.class), mapper(IExpenseMapper.class));
    }
    private void file(String id, String type, long owner, long business) {
        jdbc.update("INSERT INTO file(id,file_name,biz_type,biz_id,create_user,create_time,update_time) VALUES (?,?,?,?,?,?,?)",
                id, "fixture.png", type, business, owner, OLD_TIME, OLD_TIME);
    }

    @Test void 收入按年份筛选包含年初年末并保留其他筛选条件() {
        jdbc.update("""
                INSERT INTO income(id,user_id,amt,inc_date,inc_type_id,is_deleted) VALUES
                (1,42,100,'2025-12-31',1,0),(2,42,100,'2026-01-01',1,0),
                (3,42,100,'2026-12-31',1,0),(4,42,100,'2027-01-01',1,0),
                (5,43,100,'2026-06-01',1,0),(6,42,100,'2026-06-01',1,1),
                (7,42,100,'2026-06-01',2,0)
                """);
        var controller = incomeController();
        var request = new CommonQuery<IncomeQuery>();
        var condition = new IncomeQuery(); condition.setYear("2026"); condition.setIncTypeId("1");
        request.setCondition(condition);
        try (var auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsLong).thenReturn(42L);
            var response = controller.query(request);
            assertEquals(0, response.getCode()); assertEquals(2L, response.getData().getTotal());
            assertEquals(List.of("3", "2"), response.getData().getItems().stream().map(row -> row.getId().toString()).toList());
            condition.setStartTime("2026-07-01");
            assertEquals(List.of("3"), controller.query(request).getData().getItems().stream().map(row -> row.getId().toString()).toList());
            condition.setYear(null); condition.setStartTime(null);
            assertEquals(List.of("4", "3", "2", "1"), controller.query(request).getData().getItems().stream().map(row -> row.getId().toString()).toList());
        }
    }

    @Test void 支出按年份筛选包含年末最后一秒且排除次年零点() {
        jdbc.update("""
                INSERT INTO expense(id,user_id,amt,exp_time,exp_type_id,is_deleted) VALUES
                (1,42,100,'2025-12-31 23:59:59',1,0),(2,42,100,'2026-01-01 00:00:00',1,0),
                (3,42,100,'2026-12-31 23:59:59',1,0),(4,42,100,'2027-01-01 00:00:00',1,0),
                (5,43,100,'2026-06-01 00:00:00',1,0),(6,42,100,'2026-06-01 00:00:00',1,1),
                (7,42,100,'2026-06-01 00:00:00',2,0)
                """);
        var controller = expenseController();
        var request = new CommonQuery<ExpenseQuery>();
        var condition = new ExpenseQuery(); condition.setYear("2026"); condition.setExpTypeId("1");
        request.setCondition(condition);
        try (var auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsLong).thenReturn(42L);
            var response = controller.query(request);
            assertEquals(0, response.getCode()); assertEquals(2L, response.getData().getTotal());
            assertEquals(List.of("3", "2"), response.getData().getItems().stream().map(row -> row.getId().toString()).toList());
            condition.setStartTime(LocalDateTime.of(2026, 7, 1, 0, 0));
            assertEquals(List.of("3"), controller.query(request).getData().getItems().stream().map(row -> row.getId().toString()).toList());
            condition.setYear(null); condition.setStartTime(null);
            assertEquals(List.of("4", "3", "2", "1"), controller.query(request).getData().getItems().stream().map(row -> row.getId().toString()).toList());
        }
    }

    @Test void 修改收入维护审计字段且不修改创建信息和其他用户记录() {
        jdbc.update("""
                INSERT INTO income(id,user_id,amt,inc_date,inc_type_id,create_user,create_time,update_user,update_time)
                VALUES (1,42,100,'2026-01-01',1,99,?,7,?),(2,43,100,'2026-01-01',1,99,?,7,?)
                """, OLD_TIME, OLD_TIME, OLD_TIME, OLD_TIME);
        var controller = incomeController();
        var change = new IncomeUpdateReq(); change.setAmt(new BigDecimal("200"));
        try (var auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsLong).thenReturn(42L);
            assertTrue(controller.update(1L, change).getData());
            assertFalse(controller.update(2L, change).getData());
        }
        var saved = mapper(IIncomeMapper.class).selectById(1);
        assertEquals(new BigDecimal("200.00"), saved.getAmt());
        assertTrue(saved.getUpdateTime().isAfter(OLD_TIME)); assertEquals(42L, saved.getUpdateUser());
        assertEquals(OLD_TIME, saved.getCreateTime()); assertEquals(99L, saved.getCreateUser());
        var foreign = mapper(IIncomeMapper.class).selectById(2);
        assertEquals(new BigDecimal("100.00"), foreign.getAmt());
        assertEquals(OLD_TIME, foreign.getUpdateTime()); assertEquals(7L, foreign.getUpdateUser());
    }

    @Test void 修改支出维护审计字段且不修改创建信息和其他用户记录() {
        jdbc.update("""
                INSERT INTO expense(id,user_id,amt,exp_time,exp_type_id,create_user,create_time,update_user,update_time)
                VALUES (1,42,100,'2026-01-01 00:00:00',1,99,?,7,?),(2,43,100,'2026-01-01 00:00:00',1,99,?,7,?)
                """, OLD_TIME, OLD_TIME, OLD_TIME, OLD_TIME);
        var controller = expenseController();
        var change = new ExpenseUpdateReq(); change.setId(1L); change.setAmt(new BigDecimal("200"));
        try (var auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsLong).thenReturn(42L);
            assertEquals(0, controller.update(change).getCode());
            change.setId(2L); controller.update(change);
        }
        var saved = mapper(IExpenseMapper.class).selectById(1);
        assertEquals(new BigDecimal("200.00"), saved.getAmt()); assertEquals(saved.getAmt(), saved.getTransactionAmt());
        assertTrue(saved.getUpdateTime().isAfter(OLD_TIME)); assertEquals(42L, saved.getUpdateUser());
        assertEquals(OLD_TIME, saved.getCreateTime()); assertEquals(99L, saved.getCreateUser());
        var foreign = mapper(IExpenseMapper.class).selectById(2);
        assertEquals(new BigDecimal("100.00"), foreign.getAmt());
        assertEquals(OLD_TIME, foreign.getUpdateTime()); assertEquals(7L, foreign.getUpdateUser());
    }

    @Test void 银行卡新增更换卡面清空及删除都能执行真实SQL() {
        var files = mapper(BankCardFileMapper.class);
        var service = new BankCardService(mapper(BankCardMapper.class), mock(BankCardCrypto.class),
                mock(BankCardDictionaryGuard.class), mapper(BankCardDictionaryMapper.class),
                mapper(BankCardTagMapper.class), mapper(BankCardTagRelMapper.class), files,
                mapper(BankCardCoverTemplateMapper.class), mock(BankCardCoverTemplateService.class));
        var request = new BankCardReq();
        request.setCustomBankName("Fixture Bank"); request.setCardType("debit"); request.setStatus("normal");
        long id = Long.parseLong(service.save(42, null, request).getId());
        String first = "a".repeat(32), second = "b".repeat(32), foreign = "c".repeat(32);
        file(first, "bank_card_cover", 42, id); file(second, "bank_card_cover", 42, id);
        file(foreign, "bank_card_cover", 43, id);
        request.setCoverFileIds(List.of(second));
        service.save(42, id, request);
        assertEquals(1, jdbc.queryForObject("SELECT is_deleted FROM file WHERE id=?", Integer.class, first));
        assertEquals(0, jdbc.queryForObject("SELECT is_deleted FROM file WHERE id=?", Integer.class, second));
        request.setCoverFileIds(List.of()); service.save(42, id, request);
        assertEquals(1, jdbc.queryForObject("SELECT is_deleted FROM file WHERE id=?", Integer.class, second));
        assertEquals(0, jdbc.queryForObject("SELECT is_deleted FROM file WHERE id=?", Integer.class, foreign));
        service.delete(42, id);
        assertNull(mapper(BankCardMapper.class).selectById(id));
    }

    @Test void 银行启用状态按数字映射而不是PostgreSQL布尔结果() {
        jdbc.update("INSERT INTO sys_dict_type(dict_id,dict_type,is_enabled) VALUES (1,'bank',1)");
        jdbc.update("INSERT INTO sys_dict_data(dict_code,dict_id,dict_label,dict_value,is_enabled) VALUES (1,1,'Fixture','fixture',1)");
        assertEquals(1, mapper(BankCardDictionaryMapper.class).selectBanks().getFirst().isEnabled());
        jdbc.update("UPDATE sys_dict_type SET is_enabled=0 WHERE dict_id=1");
        session.clearCache();
        assertEquals(0, mapper(BankCardDictionaryMapper.class).selectBanks().getFirst().isEnabled());
    }

    @Test void 银行卡标签的原生查询与公共排序保持相同空值位置() {
        jdbc.update("INSERT INTO user_dict_data(id,user_id,dict_type,dict_label,sort_order) VALUES (1,42,'bank_card_tag','empty',NULL),(2,42,'bank_card_tag','zero',0),(3,43,'bank_card_tag','foreign',NULL)");
        assertEquals(List.of("1", "2"), mapper(BankCardTagMapper.class).selectTags(42).stream().map(tag -> tag.id()).toList());
    }

    @Test void 模板卡面排除指定文件及传空删除均不越过业务范围() {
        String first = "a".repeat(32), second = "b".repeat(32), other = "c".repeat(32);
        file(first, "bank_card_template_cover", 42, 10);
        file(second, "bank_card_template_cover", 42, 10);
        file(other, "bank_card_template_cover", 42, 11);
        var files = mapper(BankCardFileMapper.class);
        assertEquals(1, files.deleteTemplateCoversExcept(42, 10, second));
        assertEquals(1, files.deleteTemplateCoversExcept(42, 10, null));
        assertEquals(0, jdbc.queryForObject("SELECT is_deleted FROM file WHERE id=?", Integer.class, other));
    }

    private WereadServiceImpl weread(WereadClient client) {
        return new WereadServiceImpl(mapper(UserBindMapper.class), client, new WereadReadCache());
    }
    private void connection(long id, long owner, String platform, String meta) {
        jdbc.update("INSERT INTO user_bind(id,user_id,platform,access_token,meta_fields,update_time) VALUES (?,?,?,'wrk-fixture',CAST(? AS jsonb),?)",
                id, owner, platform, meta, OLD_TIME);
    }

    @Test void 微信读书同步和解绑保留扩展元数据并隔离用户和平台() throws Exception {
        connection(1, 42, "weread", "{\"connectionVersion\":\"v1\",\"extra\":{\"keep\":true}}");
        connection(2, 43, "weread", "{\"connectionVersion\":\"v2\"}");
        connection(3, 42, "github", "{}");
        var client = mock(WereadClient.class);
        when(client.call(anyString(), anyString(), anyMap())).thenReturn(new ObjectMapper().readTree("{\"books\":[],\"hasMore\":0}"));
        var service = weread(client);
        try (var auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsLong).thenReturn(42L);
            var result = service.sync("overall", 0);
            var stored = mapper(UserBindMapper.class).selectById(1);
            var metadata = JSON.parseObject(stored.getMetaFields());
            assertEquals(result.path("lastSyncTime").asText(), metadata.getString("lastSyncTime"));
            assertTrue(metadata.getJSONObject("extra").getBooleanValue("keep"));
            assertTrue(updated("user_bind", 1).isAfter(OLD_TIME));
            service.disconnect();
        }
        assertNull(mapper(UserBindMapper.class).selectById(1));
        assertNull(jdbc.queryForObject("SELECT access_token FROM user_bind WHERE id=1", String.class));
        var after = JSON.parseObject(jdbc.queryForObject("SELECT meta_fields::text FROM user_bind WHERE id=1", String.class));
        assertFalse(after.containsKey("lastSyncTime")); assertTrue(after.containsKey("extra"));
        assertEquals("wrk-fixture", mapper(UserBindMapper.class).selectById(2).getAccessToken());
        assertEquals("wrk-fixture", mapper(UserBindMapper.class).selectById(3).getAccessToken());
    }

    @Test void 旧连接版本其他用户及已删除记录不能写入同步时间且空元数据可解绑() {
        connection(1, 42, "weread", "{\"connectionVersion\":\"new\"}");
        var binds = mapper(UserBindMapper.class);
        var now = LocalDateTime.now();
        assertEquals(0, binds.recordWereadSync(1, 42, "old", now.toString(), now));
        assertEquals(0, binds.recordWereadSync(1, 43, "new", now.toString(), now));
        jdbc.update("UPDATE user_bind SET is_deleted=1 WHERE id=1");
        assertEquals(0, binds.recordWereadSync(1, 42, "new", now.toString(), now));
        connection(2, 42, "weread", null);
        assertEquals(1, binds.clearWereadCredentials(42, now));
        assertEquals("{}", jdbc.queryForObject("SELECT meta_fields::text FROM user_bind WHERE id=2", String.class));
    }

    @Test void 修改资料和头像显式清空都会更新审计时间且不覆盖创建时间() {
        var users = new UserServiceImpl(mapper(UserMapper.class), mock(ApiKeyMapper.class), mock(LoginSessionService.class),
                mock(LoginLogMapper.class), mock(IMailService.class), mock(RedisUtil.class), mock(UserSecondaryLockMenuMapper.class),
                mock(SecondaryLockMenuResolver.class), mock(UserAvatarFileService.class));
        var change = new UserEntity(); change.setId(42L); change.setNickname("after"); change.setUpdateUser(99L);
        var created = mapper(UserMapper.class).selectById(42).getCreateTime();
        users.updateUser(change, false);
        assertTrue(updated("sys_user", 42).isAfter(OLD_TIME));
        assertEquals(99L, mapper(UserMapper.class).selectById(42).getUpdateUser());
        jdbc.update("UPDATE sys_user SET update_time=? WHERE id=42", OLD_TIME);
        var clear = new UserEntity(); clear.setId(42L);
        users.updateUser(clear, true);
        var saved = mapper(UserMapper.class).selectById(42);
        assertTrue(saved.getUpdateTime().isAfter(OLD_TIME)); assertEquals(created, saved.getCreateTime());
    }

    @Test void 设备空日期放最后且同日期记录跨页不重不漏() {
        for (long id : List.of(1L, 2L, 3L, 4L, 5L)) {
            jdbc.update("INSERT INTO device(id,user_id,name,purchase_date) VALUES (?,42,'fixture',CAST(? AS date))",
                    id, id <= 2 ? null : "2026-11-10");
        }
        var controller = new DeviceController(mapper(IDeviceMapper.class), mock(DeviceExpenseService.class));
        var request = new CommonQuery<DeviceQuery>(); request.setCondition(new DeviceQuery()); request.setPageSize(2);
        var ids = new ArrayList<String>();
        try (var auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsLong).thenReturn(42L);
            for (int page = 1; page <= 4; page++) {
                request.setPage(page);
                var response = controller.query(request);
                assertEquals(0, response.getCode()); assertEquals(5L, response.getData().getTotal());
                response.getData().getItems().forEach(row -> ids.add(row.getId().toString()));
            }
        }
        assertEquals(List.of("5", "4", "3", "2", "1"), ids);
    }

    @Test void 正序可空排序值在前且查询保留筛选条件() {
        jdbc.update("INSERT INTO task(id,user_id,content,sort_order) VALUES (1,42,'empty',NULL),(2,42,'zero',0),(3,43,'foreign',NULL)");
        var query = new LambdaQueryWrapper<TaskEntity>().eq(TaskEntity::getUserId, 42L);
        QueryOrder.ascNullsFirst(query, TaskEntity::getSortOrder, TaskEntity::getId);
        assertEquals(List.of(1L, 2L), mapper(ITaskMapper.class).selectList(query).stream().map(TaskEntity::getId).toList());
    }

    @Test void 笔记切换隐藏保持时间但修改内容会更新时间() {
        jdbc.update("INSERT INTO memo(id,user_id,title,content,update_time) VALUES (1,42,'fixture','before',?)", OLD_TIME);
        var controller = new MemoController(mock(IMemoService.class), mapper(IMemoMapper.class));
        try (var auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsLong).thenReturn(42L);
            var hidden = new MemoUpdateReq(); hidden.setHiddenContent(true);
            controller.update(1L, hidden); assertEquals(OLD_TIME, updated("memo", 1));
            var content = new MemoUpdateReq(); content.setContent("after");
            controller.update(1L, content); assertTrue(updated("memo", 1).isAfter(OLD_TIME));
        }
    }

    @Test void 公共分类覆盖修改和隐藏维护更新时间() {
        jdbc.update("INSERT INTO time_tracker_category(id,user_id,name,update_time) VALUES (1,0,'public',?),(2,42,'override',?)", OLD_TIME, OLD_TIME);
        jdbc.update("UPDATE time_tracker_category SET template_id=1 WHERE id=2");
        var service = new TimeTrackerCategoryServiceImpl(new TimeTrackerCategoryCache(mock(ApplicationEventPublisher.class)));
        ReflectionTestUtils.setField(service, "baseMapper", mapper(ITimeTrackerCategoryMapper.class));
        ReflectionTestUtils.setField(service, "timeRecordMapper", mapper(ITimeRecordMapper.class));
        var changes = new TimeTrackerCategoryEntity(); changes.setName("changed");
        service.updateCategory(1L, changes, 42L);
        assertTrue(updated("time_tracker_category", 2).isAfter(OLD_TIME));
        jdbc.update("UPDATE time_tracker_category SET update_time=? WHERE id=2", OLD_TIME);
        session.clearCache();
        service.deleteCategory(1L, 42L);
        assertTrue(updated("time_tracker_category", 2).isAfter(OLD_TIME));
        assertEquals(0, mapper(ITimeTrackerCategoryMapper.class).selectById(2).getIsEnabled());
    }

    @Test void 文件绑定维护审计字段且不修改他人文件() {
        String own = "a".repeat(32), other = "b".repeat(32);
        file(own, "read", 42, 1); file(other, "read", 43, 1);
        var service = new FileServiceImpl(mock(MinioUtil.class), mock(MinioConfig.class),
                mock(StorageObjectLock.class), mock(DoubanCoverUrlPolicy.class));
        ReflectionTestUtils.setField(service, "baseMapper", mapper(IFileMapper.class));
        try (var auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsLong).thenReturn(42L);
            service.bindBizId(List.of(own, other), "read", 2L);
        }
        assertEquals(42L, mapper(IFileMapper.class).selectById(own).getUpdateUser());
        assertTrue(mapper(IFileMapper.class).selectById(own).getUpdateTime().isAfter(OLD_TIME));
        assertEquals(1L, mapper(IFileMapper.class).selectById(other).getBizId());
        assertEquals(OLD_TIME, mapper(IFileMapper.class).selectById(other).getUpdateTime());
    }

    @Test void 视频封面排队和任务完成维护更新时间() {
        jdbc.update("INSERT INTO b_video(id,user_id,title,url,duration_seconds,update_time) VALUES (1,42,'fixture','https://example.test/v',10,?)", OLD_TIME);
        var service = new VideoCoverService(mapper(IBVideoMapper.class), mapper(ImageImportTaskMapper.class),
                mapper(IFileMapper.class), mock(VideoCoverDownloader.class), mock(VideoCoverStorage.class));
        service.enqueue(1, 42, "https://example.test/cover.png", false);
        assertTrue(updated("b_video", 1).isAfter(OLD_TIME));
        jdbc.update("UPDATE image_import_task SET state='RUNNING',lease_token='fixture',attempts=5,update_time=? WHERE video_id=1", OLD_TIME);
        long id = jdbc.queryForObject("SELECT id FROM image_import_task WHERE video_id=1", Long.class);
        service.failed(id, "fixture", "FIXTURE_FAILURE");
        assertTrue(updated("image_import_task", id).isAfter(OLD_TIME));
        assertEquals("FAILED", mapper(IBVideoMapper.class).selectById(1).getCoverState());
    }

    @Test void 定时任务与视频写入的冲突目标匹配部分唯一索引() {
        var jobs = mapper(ScheduledJobMapper.class);
        assertEquals(1, jobs.insertIfAbsent(scheduledJob("UPSERT_TEST")));
        assertEquals(0, jobs.insertIfAbsent(scheduledJob("UPSERT_TEST")));
        jdbc.update("UPDATE sys_scheduled_job SET is_deleted = 1 WHERE job_code = 'UPSERT_TEST'");
        assertEquals(1, jobs.insertIfAbsent(scheduledJob("UPSERT_TEST")));

        var videos = mapper(IBVideoMapper.class);
        assertEquals(1, videos.insertIgnoreActive(video(1L, "BV1UPSERT")));
        assertEquals(0, videos.insertIgnoreActive(video(2L, "BV1UPSERT")));
        assertEquals(1, videos.insertIgnoreActive(video(3L, "")));
        assertEquals(1, videos.insertIgnoreActive(video(4L, "")));
    }

    @Test void 时迹分类删除落为逻辑删除() {
        var categories = mapper(ITimeTrackerCategoryMapper.class);
        var category = new TimeTrackerCategoryEntity();
        category.setUserId(42L);
        category.setName("fixture");
        category.fillCreateCommonField(42L);
        categories.insert(category);
        assertNotNull(category.getId());
        categories.deleteById(category.getId());
        assertNull(categories.selectById(category.getId()));
        assertEquals(1, jdbc.queryForObject(
                "SELECT is_deleted FROM time_tracker_category WHERE id = ?", Integer.class, category.getId()).intValue());
    }

    @Test void 审计字段和字典启用状态满足契约() {
        var audit = jdbc.queryForList("""
                SELECT c.table_name, c.column_name
                FROM information_schema.columns c
                JOIN information_schema.tables t
                  ON t.table_schema = c.table_schema
                 AND t.table_name = c.table_name
                 AND t.table_type = 'BASE TABLE'
                WHERE c.table_schema = current_schema()
                  AND c.table_name NOT IN ('flyway_schema_history', 'migration_archive')
                  AND c.column_name IN ('create_time', 'update_time')
                  AND (c.is_nullable = 'YES' OR c.column_default IS NULL)
                """);
        assertTrue(audit.isEmpty(), "审计字段仍需默认值或非空: " + audit);

        assertEquals("NO", jdbc.queryForObject("""
                SELECT is_nullable FROM information_schema.columns
                WHERE table_schema = current_schema() AND table_name = 'sys_dict_data' AND column_name = 'is_enabled'
                """, String.class));
        assertEquals("NO", jdbc.queryForObject("""
                SELECT is_nullable FROM information_schema.columns
                WHERE table_schema = current_schema() AND table_name = 'sys_dict_type' AND column_name = 'is_enabled'
                """, String.class));
        assertEquals("YES", jdbc.queryForObject("""
                SELECT is_nullable FROM information_schema.columns
                WHERE table_schema = current_schema() AND table_name = 'user_dict_data' AND column_name = 'is_enabled'
                """, String.class));
        assertEquals(3, jdbc.queryForObject("""
                SELECT count(*) FROM pg_constraint
                WHERE conname IN ('ck_sys_dict_type_enabled', 'ck_sys_dict_data_enabled', 'ck_user_dict_data_enabled')
                """, Integer.class).intValue());

        jdbc.update("INSERT INTO user_dict_data(id, user_id, dict_type, is_enabled) VALUES (9001, 42, 'fixture', NULL)");
        jdbc.update("INSERT INTO user_dict_data(id, user_id, dict_type, is_enabled) VALUES (9002, 42, 'fixture', 1)");
    }

    private ScheduledJobEntity scheduledJob(String code) {
        var job = new ScheduledJobEntity();
        job.setJobCode(code);
        job.setName("fixture");
        job.setCron("0 0 1 * * ?");
        job.setZoneId("Asia/Shanghai");
        job.setIsEnabled(1);
        job.setRevision(0);
        job.fillCreateCommonField(null);
        return job;
    }

    private BVideoEntity video(long id, String bvid) {
        var video = new BVideoEntity();
        video.setId(id);
        video.setUserId(42L);
        video.setTitle("fixture");
        video.setUrl("https://example.test/v");
        video.setDurationSeconds(1);
        video.setBvid(bvid);
        video.fillCreateCommonField(42L);
        return video;
    }
}
