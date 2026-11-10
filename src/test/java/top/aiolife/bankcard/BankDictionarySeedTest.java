package top.aiolife.bankcard;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BankDictionarySeedTest {
    @Test void PostgreSQL种子包含银行目录且不创建默认账号() throws Exception {
        String sql=Files.readString(Path.of("sql/migrations/postgresql/V20261110_02__public_seed_data.sql"));
        assertTrue(sql.contains("ICBC"));
        assertTrue(sql.contains("NFRA_"));
        assertTrue(sql.contains("/finance/bank-cards"));
        assertFalse(sql.contains("INSERT INTO \"user\""));
    }
}
