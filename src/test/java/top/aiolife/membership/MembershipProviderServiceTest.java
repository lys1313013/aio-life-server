package top.aiolife.membership;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.aiolife.membership.api.MembershipController;
import top.aiolife.membership.convertor.MembershipApiConvertor;
import top.aiolife.membership.service.IMembershipService;
import top.aiolife.membership.mapper.IMembershipMapper;
import top.aiolife.membership.mapper.MembershipProviderMapper;
import top.aiolife.membership.pojo.req.MembershipCreateReq;
import top.aiolife.membership.pojo.req.MembershipProviderReq;
import top.aiolife.membership.pojo.req.MembershipReq;
import top.aiolife.membership.service.MembershipIconCatalog;
import top.aiolife.membership.service.MembershipProviderService;
import top.aiolife.membership.service.MembershipRecordWriteService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

/** 隔离 H2、真实 Mapper SQL 与事务，覆盖跨用户边界及并发平台删除。 */
class MembershipProviderServiceTest {
    private JdbcTemplate jdbc;
    private TransactionTemplate tx;
    private MembershipProviderService providers;
    private MembershipRecordWriteService records;
    private IMembershipMapper mapper;
    private MembershipProviderMapper platformMapper;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach void setup() throws Exception {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000");
        jdbc = new JdbcTemplate(ds);
        tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        String audit = ",create_user BIGINT,create_time DATETIME,update_user BIGINT,update_time DATETIME,is_deleted INT DEFAULT 0";
        jdbc.execute("CREATE TABLE membership_provider(id BIGINT PRIMARY KEY,name VARCHAR(100),code VARCHAR(50),category VARCHAR(50),icon_key VARCHAR(64),sort_order INT,is_enabled INT" + audit + ")");
        jdbc.execute("CREATE TABLE membership_record(id BIGINT PRIMARY KEY,user_id BIGINT,name VARCHAR(100),category VARCHAR(50),provider_id BIGINT,provider VARCHAR(100),icon VARCHAR(50),color VARCHAR(50),start_date DATE,expiry_date DATE,price DECIMAL(10,2),billing_cycle VARCHAR(20),monthly_amount DECIMAL(10,2),auto_renew INT,note VARCHAR(500)" + audit + ")");
        jdbc.update("INSERT INTO membership_provider(id,name,code,category,icon_key,sort_order,is_enabled) VALUES(10,'平台A','a','video','bilibili',0,1),(20,'平台B','b','music',NULL,1,0)");
        var config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        config.addMapper(IMembershipMapper.class);
        config.addMapper(MembershipProviderMapper.class);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(ds);
        factory.setConfiguration(config);
        var session = new SqlSessionTemplate(factory.getObject());
        mapper = session.getMapper(IMembershipMapper.class);
        platformMapper = session.getMapper(MembershipProviderMapper.class);
        providers = new MembershipProviderService(platformMapper, mapper, new MembershipIconCatalog(json));
        records = new MembershipRecordWriteService(mapper, providers);
    }

    private MembershipReq req(Long providerId) {
        var req = new MembershipReq();
        req.setProviderId(providerId); req.setName("测试会员"); req.setExpiryDate(LocalDate.now().plusMonths(1));
        return req;
    }

    @Test void 全部旧记录未关联平台仍能正常查询列表() {
        var oldRecord = tx.execute(status -> records.create(1, req(null)));
        var legacyService = mock(IMembershipService.class);
        when(legacyService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(oldRecord));
        var controller = new MembershipController(records, providers, legacyService);
        try (var login = mockStatic(StpUtil.class)) {
            login.when(StpUtil::getLoginIdAsLong).thenReturn(1L);
            var result = controller.list().getData();
            assertEquals(1, result.size());
            assertNull(result.getFirst().getProviderId());
            assertNull(result.getFirst().getProviderIconKey());
        }
    }

    @Test void 默认分类可覆盖且平台目录过滤停用项() {
        var created = tx.execute(status -> records.create(1, req(10L)));
        assertEquals("video", created.getCategory());
        assertEquals("平台A", created.getProvider());
        var custom = req(10L); custom.setCategory("study");
        assertEquals("study", tx.execute(status -> records.create(1, custom)).getCategory());
        assertEquals(1, providers.list(true).size());
        assertEquals(2, providers.list(false).size());
        assertThrows(IllegalArgumentException.class, () -> tx.execute(status -> records.create(1, req(20L))));
        assertThrows(IllegalArgumentException.class, () -> tx.execute(status -> records.create(1, req(999L))));
    }

    @Test void 省略关联保留而显式空值清除且旧客户端字段映射兼容() throws Exception {
        var created = tx.execute(status -> records.create(1, req(10L)));
        var omitted = json.readValue("{\"id\":" + created.getId() + ",\"name\":\"改名\"}", MembershipReq.class);
        assertFalse(omitted.hasProviderId());
        assertEquals(10L, tx.execute(status -> records.update(1, omitted)).getProviderId());
        var cleared = json.readValue("{\"id\":" + created.getId() + ",\"providerId\":null}", MembershipReq.class);
        assertTrue(cleared.hasProviderId());
        assertNull(tx.execute(status -> records.update(1, cleared)).getProviderId());
        var create = new MembershipCreateReq(); create.setProviderId(10L);
        assertEquals(10L, MembershipApiConvertor.INSTANCE.fromMembershipCreateReq(create).getProviderId());
        assertFalse(json.writeValueAsString(cleared).contains("providerIdProvided"));
    }

    @Test void 停用历史可编辑且不能跨用户编辑或选择新停用平台() {
        var created = tx.execute(status -> records.create(1, req(10L)));
        jdbc.update("UPDATE membership_provider SET is_enabled=0,name='新平台名称' WHERE id=10");
        var edit = req(10L); edit.setId(created.getId());
        assertEquals("新平台名称", tx.execute(status -> records.update(1, edit)).getProvider());
        assertEquals("新平台名称", providers.findAll(List.of(10L)).get(10L).getName());
        assertThrows(IllegalArgumentException.class, () -> tx.execute(status -> records.update(2, edit)));
        edit.setProviderId(20L);
        assertThrows(IllegalArgumentException.class, () -> tx.execute(status -> records.update(1, edit)));
    }

    @Test void 引用平台不能删除且图标可清空非法图标被拒绝() {
        var created = tx.execute(status -> records.create(1, req(10L)));
        assertThrows(IllegalArgumentException.class, () -> tx.executeWithoutResult(status -> providers.delete(9, 10L)));
        MembershipProviderReq request = new MembershipProviderReq();
        request.setName("平台A"); request.setCode("a"); request.setCategory("video");
        tx.execute(status -> providers.save(9, 10L, request));
        assertNull(platformMapper.selectById(10L).getIconKey());
        request.setIconKey("../../application.yml");
        assertThrows(IllegalArgumentException.class, () -> tx.execute(status -> providers.save(9, 10L, request)));
        mapper.deleteById(created.getId());
        tx.executeWithoutResult(status -> providers.delete(9, 10L));
        assertNull(platformMapper.selectById(10L));
    }

    @Test void 新增引用先持锁时删除等待提交并拒绝删除() throws Exception {
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var save = pool.submit(() -> tx.execute(status -> {
                providers.lockForRecord(10L, null);
                locked.countDown();
                try { assertTrue(release.await(3, TimeUnit.SECONDS)); } catch (InterruptedException e) { throw new RuntimeException(e); }
                return records.create(1, req(10L));
            }));
            assertTrue(locked.await(3, TimeUnit.SECONDS));
            var deletion = pool.submit(() -> assertThrows(IllegalArgumentException.class,
                    () -> tx.executeWithoutResult(status -> providers.delete(9, 10L))));
            release.countDown();
            assertNotNull(save.get(5, TimeUnit.SECONDS));
            deletion.get(5, TimeUnit.SECONDS);
            assertNotNull(platformMapper.selectById(10L));
        }
    }

    @Test void 删除平台先提交后不能新增悬空引用() {
        tx.executeWithoutResult(status -> providers.delete(9, 10L));
        assertThrows(IllegalArgumentException.class, () -> tx.execute(status -> records.create(1, req(10L))));
        assertEquals(0, mapper.selectCount(null));
    }
}
