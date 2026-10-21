package top.aiolife.record.api;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.sql.Timestamp;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import top.aiolife.config.JsonConfig;
import top.aiolife.core.cache.SecondaryLockPolicy;
import top.aiolife.record.mapper.LifeFootprintMapper;
import top.aiolife.record.pojo.req.LifeFootprintSaveReq;
import top.aiolife.record.service.LifeFootprintDates;
import top.aiolife.record.service.LifeFootprintService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LifeFootprintTest {
    private LifeFootprintService service;
    private JdbcTemplate jdbc;

    @BeforeEach void setup() throws Exception {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute(Files.readString(Path.of("sql/1_init_table/2026-10-21_life_footprint.sql"))
                .replaceAll("(?m)^--.*$", "")
                .replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci", ""));
        var config = new MybatisConfiguration(); config.addMapper(LifeFootprintMapper.class);
        var factory = new MybatisSqlSessionFactoryBean(); factory.setDataSource(ds); factory.setConfiguration(config);
        service = new LifeFootprintService(new SqlSessionTemplate(factory.getObject()).getMapper(LifeFootprintMapper.class));
    }

    private LifeFootprintSaveReq request() {
        var req = new LifeFootprintSaveReq();
        req.setStartDate("2016-09-01"); req.setEndDate("2020-06-30");
        req.setPlaceName(" 示例住所 "); req.setAddress(" 示例地址 "); req.setNote(" 备注 ");
        req.setLongitude(new BigDecimal("120.127128")); req.setLatitude(new BigDecimal("30.273297"));
        req.setCoordinateSystem("GCJ02");
        return req;
    }

    @Test void testDates_完整日期及至今特殊值校验() {
        var today = LocalDate.of(2026, 10, 21);
        assertDoesNotThrow(() -> LifeFootprintDates.validate("2016-12-30", "2016-12-30", today));
        assertDoesNotThrow(() -> LifeFootprintDates.validate("2024-02-29", "9999-12-31", today));
        for (String value : new String[]{"2016", "2020-06", "0000-01-01", "0999-01-01", "2023-02-29", "2020-00-01", "2020-13-01", "2020-1-1", "no"}) {
            assertThrows(ResponseStatusException.class, () -> LifeFootprintDates.parse(value));
        }
        assertThrows(ResponseStatusException.class, () -> LifeFootprintDates.validate("9999-12-31", "9999-12-31", today));
        assertThrows(ResponseStatusException.class, () -> LifeFootprintDates.validate("2020-07-01", "2020-06-30", today));
        assertThrows(ResponseStatusException.class, () -> LifeFootprintDates.validate("2020-01-01", "2027-01-01", today));
        assertThrows(ResponseStatusException.class, () -> LifeFootprintDates.validate("2020-01-01", "9999-12-30", today));
        assertThrows(ResponseStatusException.class, () -> LifeFootprintDates.validate("2020-01-01", null, today));
        assertEquals(LocalDate.of(2024, 2, 29), LifeFootprintDates.parse("2024-02-29"));
    }

    @Test void testCrud_字段可清空无版本连续编辑及逻辑删除() {
        var req = request(); var first = service.create(1, req);
        assertEquals(LocalDate.of(2016, 9, 1), first.getStartDate()); assertEquals(LocalDate.of(2020, 6, 30), first.getEndDate());
        assertEquals("示例住所", first.getPlaceName()); assertEquals("备注", first.getNote());
        req.setNote(null); req.setAddress(null); req.setEndDate("9999-12-31");
        service.update(1, first.getId(), req);
        var persisted = service.get(1, first.getId());
        assertNull(persisted.getNote()); assertNull(persisted.getAddress()); assertEquals(LifeFootprintDates.PRESENT, persisted.getEndDate());
        req.setNote("再次编辑");
        service.update(1, first.getId(), req);
        assertEquals("再次编辑", service.get(1, first.getId()).getNote());
        assertNotNull(jdbc.queryForObject("SELECT update_time FROM life_footprint WHERE id=?", Timestamp.class, first.getId()));
        service.delete(1, first.getId());
        assertTrue(service.list(1).isEmpty());
        assertThrows(ResponseStatusException.class, () -> service.get(1, first.getId()));
        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> service.update(1, first.getId(), req)).getStatusCode().value());
        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> service.delete(1, first.getId())).getStatusCode().value());
        assertEquals(1, jdbc.queryForObject("SELECT is_deleted FROM life_footprint WHERE id=?", Integer.class, first.getId()));
    }

    @Test void testOwner_所有读写只操作本人记录且地图读取全量() {
        var first = service.create(1, request());
        service.create(1, request()); service.create(2, request());
        assertEquals(2, service.list(1).size()); assertEquals(1, service.list(2).size());
        assertThrows(ResponseStatusException.class, () -> service.get(2, first.getId()));
        assertThrows(ResponseStatusException.class, () -> service.update(2, first.getId(), request()));
        assertThrows(ResponseStatusException.class, () -> service.delete(2, first.getId()));
        assertEquals("示例住所", service.get(1, first.getId()).getPlaceName());
    }

    @Test void testPresent_至今往返及改回实际结束日期() throws Exception {
        var req = request(); req.setEndDate("9999-12-31");
        var first = service.create(1, req);
        assertEquals(LifeFootprintDates.PRESENT, service.get(1, first.getId()).getEndDate());
        var json = new JsonConfig().jacksonObjectMapper(new Jackson2ObjectMapperBuilder());
        var tree = json.readTree(json.writeValueAsString(first));
        assertEquals("9999-12-31", tree.path("endDate").asText());
        assertFalse(tree.has("ongoing")); assertFalse(tree.has("requestKey"));
        req.setEndDate("2020-06-30");
        service.update(1, first.getId(), req);
        assertEquals(LocalDate.of(2020, 6, 30), service.get(1, first.getId()).getEndDate());
    }

    @Test void testJson_ID字符串坐标数值且不泄漏归属审计字段() throws Exception {
        var result = service.create(1, request());
        var json = new JsonConfig().jacksonObjectMapper(new Jackson2ObjectMapperBuilder());
        var tree = json.readTree(json.writeValueAsString(result));
        assertTrue(tree.path("id").isTextual()); assertTrue(tree.path("longitude").isNumber());
        assertEquals("2016-09-01", tree.path("startDate").asText());
        assertEquals("2020-06-30", tree.path("endDate").asText());
        assertFalse(tree.has("version"));
        assertFalse(tree.has("ongoing")); assertFalse(tree.has("requestKey"));
        assertFalse(tree.has("userId")); assertFalse(tree.has("createUser"));
    }

    @Test void testHttp_校验坐标契约并使用登录身份() throws Exception {
        var business = mock(LifeFootprintService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new LifeFootprintController(business)).build();
        var json = new JsonConfig().jacksonObjectMapper(new Jackson2ObjectMapperBuilder());
        try (var login = mockStatic(StpUtil.class)) {
            login.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            var body = json.valueToTree(request()); ((com.fasterxml.jackson.databind.node.ObjectNode) body).put("userId", "8");
            mvc.perform(post("/life-footprints").contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
            verify(business).create(eq(7L), any());
            mvc.perform(delete("/life-footprints/9223372036854775807"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
            verify(business).delete(7L, Long.MAX_VALUE);
            for (String date : new String[]{"2016", "2016-09", "", null}) {
                var partial = request(); partial.setStartDate(date);
                mvc.perform(post("/life-footprints").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(partial)))
                        .andExpect(status().isBadRequest());
                partial = request(); partial.setEndDate(date);
                mvc.perform(post("/life-footprints").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(partial)))
                        .andExpect(status().isBadRequest());
            }
            var invalid = request(); invalid.setLatitude(new BigDecimal("91"));
            mvc.perform(post("/life-footprints").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(invalid)))
                    .andExpect(status().isBadRequest());
            invalid = request(); invalid.setCoordinateSystem("BD09");
            mvc.perform(post("/life-footprints").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(invalid)))
                    .andExpect(status().isBadRequest());
        }
        assertTrue(SecondaryLockPolicy.requestMenus("/life-footprints/map/search").contains("/record/life-footprints"));
        assertFalse(SecondaryLockPolicy.requestMenus("/life-footprints-other").contains("/record/life-footprints"));
    }
}
