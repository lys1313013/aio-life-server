package top.aiolife.bankcard;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BankDictionarySeedTest {
    @Test void PostgreSQL种子包含银行目录且不创建默认账号() throws Exception {
        String sql=Files.readString(Path.of("sql/migrations/V20261110_02_005__bank_dictionary.sql"));
        assertTrue(sql.contains("ICBC"));
        assertTrue(sql.contains("NFRA_"));
        assertFalse(sql.contains("INSERT INTO \"sys_menu\""));
        String menus=Files.readString(Path.of("sql/migrations/V20261110_02_006__menus.sql"));
        assertTrue(menus.contains("/finance/bank-cards"));
        assertFalse(sql.contains("INSERT INTO \"user\""));
    }
}
