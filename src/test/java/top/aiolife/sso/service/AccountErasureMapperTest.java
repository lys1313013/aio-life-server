package top.aiolife.sso.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import top.aiolife.sso.mapper.AccountErasureMapper;
import static org.junit.jupiter.api.Assertions.*;

/** 从当前完整结构获得列清单，用真实 SQL 验证归属和软删除行清理，不连接实际用户数据库。 */
class AccountErasureMapperTest {
    @Test void 清理包括软删除记录且保留其他账号和公共预设() throws Exception {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:erase_" + UUID.randomUUID() + ";MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1");
        var jdbc = new JdbcTemplate(source);
        var tablePattern = Pattern.compile("CREATE TABLE(?: IF NOT EXISTS)? `([^`]+)` \\((.*?)\\) ENGINE", Pattern.DOTALL);
        var columnPattern = Pattern.compile("(?m)^\\s*`([^`]+)`");
        var tables = new LinkedHashMap<String, List<String>>();
        var matcher = tablePattern.matcher(Files.readString(Path.of("sql/1_init_table/2026-08-18_init_all_tables.sql")));
        while (matcher.find()) {
            String table = matcher.group(1);
            var columns = columnPattern.matcher(matcher.group(2)).results().map(m -> m.group(1)).toList();
            tables.put(table, columns);
            var definitions = columns.stream().map(column -> "`" + column + "` " +
                    (column.endsWith("_at") || column.endsWith("_time") ? "TIMESTAMP" :
                     column.equals("id") || column.endsWith("_id") || column.equals("create_user") || column.equals("is_deleted") ? "BIGINT" : "VARCHAR(500)")).toList();
            jdbc.execute("CREATE TABLE `" + table + "` (" + String.join(",", definitions) + ")");
            if (columns.contains("user_id")) {
                jdbc.update("INSERT INTO `" + table + "` (id,user_id) VALUES(1,7),(2,8),(3,NULL)");
                if (columns.contains("is_deleted")) jdbc.update("UPDATE `" + table + "` SET is_deleted=1 WHERE user_id=7");
            }
        }
        jdbc.update("INSERT INTO user(id,username,email,is_deleted,erasure_requested_at) VALUES(7,'fixture','fixture@example.test',0,?)", LocalDateTime.now());
        jdbc.update("INSERT INTO file(id,create_user,biz_type) VALUES(11,7,'avatar'),(12,8,'avatar'),(13,7,'bank_card_template_cover')");
        jdbc.update("INSERT INTO thought_rela_event(id,thought_id,create_user) VALUES(1,1,8),(2,2,8)");
        jdbc.update("INSERT INTO record_article(id,create_user) VALUES(1,7),(2,8)");
        jdbc.update("INSERT INTO record_article_annotation(id,article_id,create_user) VALUES(1,1,8),(2,2,8)");
        var config = new Configuration(new Environment("fixture", new JdbcTransactionFactory(), source));
        config.setMapUnderscoreToCamelCase(true); config.addMapper(AccountErasureMapper.class);
        try (var session = new SqlSessionFactoryBuilder().build(config).openSession(true)) {
            var mapper = session.getMapper(AccountErasureMapper.class);
            var now = LocalDateTime.now();
            assertEquals(1, mapper.request(7, now, now.plusMinutes(5)));
            assertEquals(List.of(), mapper.pending(now));
            assertEquals(List.of(7L), mapper.pending(now.plusMinutes(6)));
            jdbc.update("UPDATE notification_delivery SET status='PENDING', payload_ciphertext='fixture'");
            jdbc.execute("ALTER TABLE notification_delivery ALTER COLUMN payload_ciphertext SET NOT NULL");
            assertEquals(1, mapper.cancelNotifications(7));
            assertEquals("", jdbc.queryForObject("SELECT payload_ciphertext FROM notification_delivery WHERE user_id=7", String.class));
            mapper.purgePersonalRecords(7, "fixture@example.test");
            for (var table : tables.entrySet()) if (table.getValue().contains("user_id")) {
                assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM `" + table.getKey() + "` WHERE user_id=7", Long.class), table.getKey());
                assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM `" + table.getKey() + "` WHERE user_id=8", Long.class), table.getKey());
                assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM `" + table.getKey() + "` WHERE user_id IS NULL", Long.class), table.getKey());
            }
            assertEquals(2L, jdbc.queryForObject("SELECT COUNT(*) FROM file", Long.class));
            assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM thought_rela_event", Long.class));
            assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM record_article_annotation", Long.class));
            assertEquals(1, mapper.finish(7));
        }
    }
}
