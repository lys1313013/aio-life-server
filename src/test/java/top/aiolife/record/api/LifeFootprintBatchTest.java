package top.aiolife.record.api;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.web.server.ResponseStatusException;
import top.aiolife.config.JsonConfig;
import top.aiolife.record.mapper.LifeFootprintMapper;
import top.aiolife.record.pojo.req.LifeFootprintBatchItemReq;
import top.aiolife.record.pojo.req.LifeFootprintBatchSaveReq;
import top.aiolife.record.service.LifeFootprintService;
import top.aiolife.system.service.SystemRegionService;
import top.aiolife.support.CurrentSchema;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LifeFootprintBatchTest {
    private LifeFootprintService service;
    private JdbcTemplate jdbc;

    @BeforeEach void setup() throws Exception {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute(CurrentSchema.h2Table("life_footprint"));
        var config = new MybatisConfiguration();
        config.addMapper(LifeFootprintMapper.class);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(ds); factory.setConfiguration(config);
        var target = new LifeFootprintService(new SqlSessionTemplate(factory.getObject())
                .getMapper(LifeFootprintMapper.class), mock(SystemRegionService.class));
        var proxy = new ProxyFactory(target);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(ds),
                new AnnotationTransactionAttributeSource()));
        service = (LifeFootprintService) proxy.getProxy();
    }

    private LifeFootprintBatchItemReq item(Long id, String name) {
        var req = new LifeFootprintBatchItemReq();
        req.setId(id); req.setStartDate("2021-07-25"); req.setEndDate("9999-12-31");
        req.setPlaceName(name); req.setRemark("备注"); req.setRegionCode("110101001");
        req.setLongitude(new BigDecimal("116.312345")); req.setLatitude(new BigDecimal("40.071234"));
        req.setCoordinateSystem("GCJ02");
        return req;
    }

    private LifeFootprintBatchSaveReq batch(List<LifeFootprintBatchItemReq> items, Long... deleted) {
        var req = new LifeFootprintBatchSaveReq();
        req.setItems(items); req.setDeletedIds(List.of(deleted));
        return req;
    }

    @Test void testBatch_一次新增修改删除且保留同日同点的独立记录() {
        var first = service.create(1, item(null, "住所"));
        var removed = service.create(1, item(null, "旧地点"));
        var untouched = service.create(1, item(null, "未提交的同日期地点"));
        var changed = item(first.getId(), "新住所"); changed.setRemark(null);
        changed.setEndDate("2026-01-01");
        var added = item(null, "同位置的另一条明细"); added.setEndDate("2026-01-01");
        var saved = service.saveBatch(1, batch(List.of(changed, added), removed.getId()));
        assertEquals(2, saved.size()); assertEquals(first.getId(), saved.getFirst().getId());
        assertNotEquals(first.getId(), saved.getLast().getId());
        assertEquals(LocalDate.of(2026, 1, 1), saved.getLast().getEndDate());
        assertNull(service.get(1, first.getId()).getRemark());
        assertEquals(3, service.list(1).size());
        assertEquals("未提交的同日期地点", service.get(1, untouched.getId()).getPlaceName());
        assertThrows(ResponseStatusException.class, () -> service.get(1, removed.getId()));
    }

    @Test void testTransaction_数据库在中途失败时新增更新删除全部回滚() {
        var first = service.create(1, item(null, "住所"));
        var removed = service.create(1, item(null, "原地点"));
        jdbc.execute("ALTER TABLE life_footprint ADD CONSTRAINT test_failure CHECK (place_name <> '触发回滚')");
        assertThrows(RuntimeException.class, () -> service.saveBatch(1, batch(List.of(
                item(first.getId(), "已更新"), item(null, "已新增"), item(null, "触发回滚")), removed.getId())));
        assertEquals(2, service.list(1).size());
        assertEquals("住所", service.get(1, first.getId()).getPlaceName());
        assertEquals("原地点", service.get(1, removed.getId()).getPlaceName());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM life_footprint", Integer.class));
    }

    @Test void testOwner_越权不存在及已删除记录使整批失败() {
        var own = service.create(1, item(null, "自己"));
        var other = service.create(2, item(null, "其他用户"));
        var removed = service.create(1, item(null, "已删除")); service.delete(1, removed.getId());
        for (Long id : List.of(other.getId(), removed.getId(), Long.MAX_VALUE)) {
            assertThrows(ResponseStatusException.class, () -> service.saveBatch(1,
                    batch(List.of(item(own.getId(), "改动"), item(id, "无效")))));
            assertThrows(ResponseStatusException.class, () -> service.saveBatch(1,
                    batch(List.of(item(own.getId(), "改动")), id)));
        }
        assertEquals("自己", service.get(1, own.getId()).getPlaceName());
        assertEquals("其他用户", service.get(2, other.getId()).getPlaceName());
    }

    @Test void testValidation_重复ID交叉删除不同日期与无操作均拒绝() {
        var own = service.create(1, item(null, "自己"));
        var changed = item(own.getId(), "改动");
        assertThrows(ResponseStatusException.class, () -> service.saveBatch(1, batch(List.of(changed, changed))));
        assertThrows(ResponseStatusException.class, () -> service.saveBatch(1, batch(List.of(changed), own.getId())));
        assertThrows(ResponseStatusException.class, () -> service.saveBatch(1, batch(List.of(), own.getId(), own.getId())));
        assertThrows(ResponseStatusException.class, () -> service.saveBatch(1, batch(List.of())));
        var different = item(null, "新地点"); different.setStartDate("2021-07-26");
        assertThrows(ResponseStatusException.class, () -> service.saveBatch(1, batch(List.of(changed, different))));
        assertEquals("自己", service.get(1, own.getId()).getPlaceName());
        assertTrue(service.saveBatch(1, batch(List.of(), own.getId())).isEmpty());
        assertTrue(service.list(1).isEmpty());
    }

    @Test void testHttp_嵌套校验登录归属及大整数ID字符串响应() throws Exception {
        var business = mock(LifeFootprintService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new LifeFootprintController(business))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(
                        new JsonConfig().jacksonObjectMapper(new Jackson2ObjectMapperBuilder())))
                .build();
        var json = new JsonConfig().jacksonObjectMapper(new Jackson2ObjectMapperBuilder());
        var saved = service.saveBatch(1, batch(List.of(item(null, "住所"))));
        when(business.saveBatch(eq(7L), any())).thenReturn(saved);
        try (var login = mockStatic(StpUtil.class)) {
            login.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            mvc.perform(post("/life-footprints/batch").contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(batch(List.of(item(Long.MAX_VALUE, "住所"))))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data[0].id").value(saved.getFirst().getId().toString()));
            verify(business).saveBatch(eq(7L), argThat(r -> r.getItems().getFirst().getId().equals(Long.MAX_VALUE)));
            var invalid = item(null, ""); invalid.setLatitude(new BigDecimal("91"));
            mvc.perform(post("/life-footprints/batch").contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(batch(List.of(invalid)))))
                    .andExpect(status().isBadRequest());
            mvc.perform(post("/life-footprints/batch").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"items\":[null],\"deletedIds\":[]}"))
                    .andExpect(status().isBadRequest());
            mvc.perform(post("/life-footprints/batch").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"items\":[],\"deletedIds\":[null]}"))
                    .andExpect(status().isBadRequest());
        }
    }
}
