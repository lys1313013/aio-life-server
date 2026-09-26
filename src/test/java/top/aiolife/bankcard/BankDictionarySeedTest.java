package top.aiolife.bankcard;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BankDictionarySeedTest {
    @Test
    void seed_完整初始化并保留已有引用与管理员配置() throws Exception {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE sys_dict_type(dict_id BIGINT PRIMARY KEY,dict_name VARCHAR(100),dict_type VARCHAR(100),status CHAR(1),is_deleted INT,create_user BIGINT,update_user BIGINT)");
        jdbc.execute("CREATE TABLE sys_dict_data(dict_code BIGINT PRIMARY KEY,dict_id BIGINT,dict_sort INT,dict_label VARCHAR(100),dict_value VARCHAR(100),status CHAR(1),is_deleted INT,create_user BIGINT,update_user BIGINT,remark VARCHAR(500))");
        jdbc.execute("CREATE TABLE sys_menu(id BIGINT PRIMARY KEY,parent_id BIGINT,name VARCHAR(100),path VARCHAR(100),component VARCHAR(100),meta VARCHAR(500),sort INT,status INT,is_deleted INT,create_user BIGINT,update_user BIGINT)");
        jdbc.execute("CREATE TABLE bank_card(id BIGINT PRIMARY KEY,bank_id BIGINT)");
        try (var connection = source.getConnection()) {
            execute(connection, "2026-09-26_bank_card.sql");
            assertEquals(3132, jdbc.queryForObject("SELECT COUNT(*) FROM sys_dict_data", Integer.class));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys_dict_type WHERE dict_type='bank'", Integer.class));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys_menu WHERE id=1605 AND path='/finance/bank-cards'", Integer.class));
            jdbc.update("INSERT INTO bank_card VALUES(1,92626101)");
            jdbc.update("UPDATE sys_menu SET sort=99 WHERE id=1605");
            jdbc.update("UPDATE sys_dict_data SET dict_label='我的工行名称',status='1',dict_sort=999 WHERE dict_value='ICBC'");
            jdbc.update("UPDATE sys_dict_data SET is_deleted=1 WHERE dict_value='ABC'");
            execute(connection, "2026-09-26_bank_card.sql");
            execute(connection, "2026-09-26_bank_card.sql");
            assertEquals(3132, jdbc.queryForObject("SELECT COUNT(*) FROM sys_dict_data", Integer.class));
            assertEquals(3117, jdbc.queryForObject("SELECT COUNT(*) FROM sys_dict_data WHERE dict_value LIKE 'NFRA_%'", Integer.class));
            assertEquals(3132, jdbc.queryForObject("SELECT COUNT(DISTINCT dict_value) FROM sys_dict_data", Integer.class));
            assertEquals("我的工行名称", jdbc.queryForObject("SELECT d.dict_label FROM bank_card c JOIN sys_dict_data d ON d.dict_code=c.bank_id", String.class));
            assertEquals("1", jdbc.queryForObject("SELECT status FROM sys_dict_data WHERE dict_value='ICBC'", String.class));
            assertEquals(999, jdbc.queryForObject("SELECT dict_sort FROM sys_dict_data WHERE dict_value='ICBC'", Integer.class));
            assertEquals(1, jdbc.queryForObject("SELECT is_deleted FROM sys_dict_data WHERE dict_value='ABC'", Integer.class));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sys_dict_data WHERE dict_label LIKE '%信托%' OR dict_label LIKE '%财务公司%'", Integer.class));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys_menu", Integer.class));
            assertEquals(99, jdbc.queryForObject("SELECT sort FROM sys_menu WHERE id=1605", Integer.class));
            assertEquals(1177, jdbc.queryForObject("SELECT COUNT(*) FROM sys_dict_data WHERE remark LIKE '%；村镇银行；%'", Integer.class));
        }
    }

    private void execute(java.sql.Connection connection, String filename) throws Exception {
        // H2 无 MySQL USE / DROP TEMPORARY TABLE 语法，其他 DML 原样执行。
        String sql = Files.readString(Path.of("sql/2_ini_data", filename))
                .replace("USE `aio_life`;", "")
                .replace("DROP TEMPORARY TABLE", "DROP TABLE");
        ScriptUtils.executeSqlScript(connection, new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8)));
    }
}
