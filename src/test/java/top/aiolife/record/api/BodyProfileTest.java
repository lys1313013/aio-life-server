package top.aiolife.record.api;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
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
import top.aiolife.record.mapper.BodyProfileMapper;
import top.aiolife.record.pojo.req.BodyProfileSaveReq;
import top.aiolife.record.service.BodyProfileService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 合成用户和隔离 H2，执行真实 MyBatis SQL，不读写运行中的用户数据。 */
class BodyProfileTest {
    BodyProfileService service;
    JdbcTemplate jdbc;

    @BeforeEach void setup() throws Exception {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(ds);
        String full = Files.readString(Path.of("sql/1_init_table/2026-08-18_init_all_tables.sql"));
        int start = full.indexOf("CREATE TABLE IF NOT EXISTS `user_body_profile`");
        String ddl = full.substring(start, full.indexOf(";", start));
        jdbc.execute(ddl.substring(0, ddl.indexOf(" ENGINE=")));
        var config = new MybatisConfiguration();
        config.addMapper(BodyProfileMapper.class);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(ds);
        factory.setConfiguration(config);
        service = new BodyProfileService(new SqlSessionTemplate(factory.getObject()).getMapper(BodyProfileMapper.class));
    }

    BodyProfileSaveReq request(int version) {
        var req = new BodyProfileSaveReq();
        req.setVersion(version);
        req.setHeightCm(new BigDecimal("178"));
        req.setWeightKg(new BigDecimal("72"));
        return req;
    }

    @Test void testSave_单份覆盖清空及BMI计算() {
        assertEquals(0, service.get(1).getVersion());
        assertNull(service.get(1).getBmi());
        var req = request(0);
        req.setUpperArmCm(new BigDecimal("32.5"));
        req.setShoeSize("42");
        req.setTopSize(" L ");
        var first = service.save(1, req);
        assertEquals(new BigDecimal("22.7"), first.getBmi());
        assertEquals("EU", first.getShoeSystem());
        assertEquals("L", first.getTopSize());
        var update = request(first.getVersion());
        update.setWeightKg(new BigDecimal("80"));
        var second = service.save(1, update);
        assertEquals(first.getId(), second.getId());
        assertEquals(2, second.getVersion());
        assertEquals(new BigDecimal("25.2"), second.getBmi());
        var persisted = service.get(1);
        assertNull(persisted.getUpperArmCm());
        assertNull(persisted.getTopSize());
        assertNull(persisted.getShoeSize());
        assertNull(persisted.getShoeSystem());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM user_body_profile", Integer.class));
        update.setVersion(second.getVersion());
        update.setHeightCm(null);
        assertNull(service.save(1, update).getBmi());
    }

    @Test void testOwner_不同用户不可互相读取或覆盖() {
        var first = service.save(1, request(0));
        assertNull(service.get(2).getId());
        var req = request(0);
        req.setWeightKg(new BigDecimal("60"));
        var second = service.save(2, req);
        assertNotEquals(first.getId(), second.getId());
        assertEquals(new BigDecimal("72.0"), service.get(1).getWeightKg());
        assertEquals(new BigDecimal("60.0"), service.get(2).getWeightKg());
    }

    @Test void testVersion_旧表单及并发首次保存不能覆盖最新值() {
        var first = service.save(1, request(0));
        assertThrows(ResponseStatusException.class, () -> service.save(1, request(0)));
        var update = request(first.getVersion());
        update.setWeightKg(new BigDecimal("80"));
        service.save(1, update);
        assertThrows(ResponseStatusException.class, () -> service.save(1, request(first.getVersion())));
        assertEquals(new BigDecimal("80.0"), service.get(1).getWeightKg());
    }

    @Test void testJson_ID为字符串而BMI和版本为数值() throws Exception {
        var response = service.save(1, request(0));
        var json = new JsonConfig().jacksonObjectMapper(new Jackson2ObjectMapperBuilder());
        var tree = json.readTree(json.writeValueAsString(response));
        assertTrue(tree.get("id").isTextual());
        assertTrue(tree.get("version").isInt());
        assertTrue(tree.get("bmi").isNumber());
        assertTrue(tree.get("updateTime").isTextual());
        assertFalse(tree.has("userId"));
        assertFalse(tree.has("createUser"));
    }

    @Test void testHttp_客户端身份和计算字段无效且校验阻止非法保存() throws Exception {
        var business = mock(BodyProfileService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new BodyProfileController(business)).build();
        try (var login = mockStatic(StpUtil.class)) {
            login.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            mvc.perform(put("/body-profile").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"version\":0,\"userId\":8,\"id\":9,\"bmi\":1,\"heightCm\":178}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
            verify(business).save(eq(7L), argThat(req -> req.getHeightCm().intValueExact() == 178));
            mvc.perform(get("/body-profile")).andExpect(status().isOk());
            verify(business).get(7L);
        }
        clearInvocations(business);
        for (String body : new String[]{"{}", "{\"version\":0,\"weightKg\":0}",
                "{\"version\":0,\"bodyFatPercent\":100}", "{\"version\":0,\"upperArmCm\":32.55}",
                "{\"version\":0,\"gender\":\"invalid\"}", "{\"version\":0,\"shoeSize\":\"EU 42\"}",
                "{\"version\":0,\"shoeSize\":\"0\"}"}) {
            mvc.perform(put("/body-profile").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(business);
    }
}
