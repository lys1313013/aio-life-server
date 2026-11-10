package top.aiolife.sso.api;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import top.aiolife.config.JsonConfig;
import top.aiolife.core.exception.ExceptionHandle;
import top.aiolife.sso.mapper.ApiKeyMapper;
import top.aiolife.sso.service.impl.ApiKeyServiceImpl;

import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 隔离 H2 夹具验证 HTTP 入参、字段隔离和归属校验，不替代 PostgreSQL 方言验收。 */
class ApiKeyHttpTest {
    private JdbcTemplate jdbc;
    private MockMvc mvc;

    @BeforeEach
    void setup() throws Exception {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:key_" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("""
                CREATE TABLE api_key(id BIGINT PRIMARY KEY, user_id BIGINT, api_key VARCHAR(128), remark VARCHAR(255),
                expired_at TIMESTAMP, create_user BIGINT, update_user BIGINT, create_time TIMESTAMP,
                update_time TIMESTAMP, is_deleted INT DEFAULT 0)
                """);
        jdbc.update("""
                INSERT INTO api_key(id,user_id,api_key,remark,expired_at,create_time)
                VALUES(9007199254740993,11,'ak-synthetic-test-secret-123456789','原备注','2030-01-01 00:00:00','2026-01-01 00:00:00')
                """);
        var config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        config.addMapper(ApiKeyMapper.class);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(source);
        factory.setConfiguration(config);
        var service = new ApiKeyServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", new SqlSessionTemplate(factory.getObject()).getMapper(ApiKeyMapper.class));
        var json = new JsonConfig().jacksonObjectMapper(Jackson2ObjectMapperBuilder.json());
        mvc = MockMvcBuilders.standaloneSetup(new ApiKeyController(service))
                .setControllerAdvice(new ExceptionHandle())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(json)).build();
    }

    @Test
    void 修改备注保留密钥和有效期且响应脱敏大ID保持字符串() throws Exception {
        try (var login = mockStatic(StpUtil.class)) {
            login.when(StpUtil::getLoginIdAsLong).thenReturn(11L);
            mvc.perform(put("/api-key/9007199254740993").contentType("application/json")
                    .content("{\"remark\":\"  新备注  \",\"apiKey\":\"hijack\",\"userId\":\"22\",\"expiredAt\":null}"))
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.id").value("9007199254740993"))
                    .andExpect(jsonPath("$.data.remark").value("新备注"))
                    .andExpect(jsonPath("$.data.apiKey").value("ak-synth***6789"))
                    .andExpect(jsonPath("$.data.expiredAt").value("2030-01-01 00:00:00"));
        }
        var row = jdbc.queryForMap("SELECT * FROM api_key");
        assertEquals("ak-synthetic-test-secret-123456789", row.get("api_key"));
        assertEquals(11L, row.get("user_id"));
        assertEquals(11L, row.get("update_user"));
        assertNotNull(row.get("update_time"));
    }

    @Test
    void 他人已删除和不存在的密钥均不能修改() throws Exception {
        try (var login = mockStatic(StpUtil.class)) {
            login.when(StpUtil::getLoginIdAsLong).thenReturn(22L);
            mvc.perform(put("/api-key/9007199254740993").contentType("application/json").content("{\"remark\":\"攻击\"}"))
                    .andExpect(status().isNotFound());
            login.when(StpUtil::getLoginIdAsLong).thenReturn(11L);
            jdbc.update("UPDATE api_key SET is_deleted=1");
            mvc.perform(put("/api-key/9007199254740993").contentType("application/json").content("{\"remark\":\"修改\"}"))
                    .andExpect(status().isNotFound());
            mvc.perform(put("/api-key/999").contentType("application/json").content("{\"remark\":\"修改\"}"))
                    .andExpect(status().isNotFound());
        }
        assertEquals("原备注", jdbc.queryForObject("SELECT remark FROM api_key", String.class));
    }

    @Test
    void 空白缺失和超长备注被拒绝() throws Exception {
        try (var login = mockStatic(StpUtil.class)) {
            login.when(StpUtil::getLoginIdAsLong).thenReturn(11L);
            for (String body : new String[]{"{}", "{\"remark\":\"   \"}", "{\"remark\":\"" + "a".repeat(256) + "\"}"}) {
                mvc.perform(put("/api-key/9007199254740993").contentType("application/json").content(body))
                        .andExpect(jsonPath("$.code").value(not(0)));
            }
        }
        assertEquals("原备注", jdbc.queryForObject("SELECT remark FROM api_key", String.class));
    }
}
