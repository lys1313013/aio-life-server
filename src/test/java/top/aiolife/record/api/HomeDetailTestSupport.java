package top.aiolife.record.api;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import top.aiolife.config.JsonConfig;
import top.aiolife.core.exception.ExceptionHandle;
import top.aiolife.record.mapper.IBVideoMapper;
import top.aiolife.record.mapper.IMemoMapper;

/** 详情测试共享真实表结构、MyBatis-Plus 和生产 JSON/异常转换；仅身份使用测试替身。 */
final class HomeDetailTestSupport implements AutoCloseable {
    final JdbcTemplate jdbc;
    final SqlSession session;

    HomeDetailTestSupport() throws Exception {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:home_details_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(source);
        String schema = Files.readString(Path.of("sql/1_init_table/2026-08-18_init_all_tables.sql"));
        for (String table : List.of("memo", "b_video")) {
            int start = schema.indexOf("CREATE TABLE IF NOT EXISTS `" + table + "`");
            String sql = schema.substring(start, schema.indexOf(';', start))
                    .replaceAll(" COLLATE [a-zA-Z0-9_]+", "")
                    .replaceAll(" CHARACTER SET [a-zA-Z0-9_]+", "")
                    .replace("`title`(100)", "`title`");
            jdbc.execute(sql);
        }
        var configuration = new MybatisConfiguration();
        configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(), source));
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(IMemoMapper.class);
        configuration.addMapper(IBVideoMapper.class);
        session = new MybatisSqlSessionFactoryBuilder().build(configuration).openSession();
    }

    MockMvc mvc(Object controller) {
        var json = new JsonConfig().jacksonObjectMapper(new Jackson2ObjectMapperBuilder());
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ExceptionHandle())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(json)).build();
    }

    @Override
    public void close() {
        session.close();
        jdbc.execute("DROP ALL OBJECTS");
    }
}
