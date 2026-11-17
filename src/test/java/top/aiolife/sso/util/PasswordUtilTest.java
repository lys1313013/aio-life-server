package top.aiolife.sso.util;

import cn.hutool.crypto.digest.BCrypt;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordUtilTest {
    @Test
    void 相同密码每次使用随机盐且仅正确密码能验证() {
        String first = PasswordUtil.hashPassword("0012-password");
        String second = PasswordUtil.hashPassword("0012-password");
        assertTrue(first.startsWith("$2a$10$"));
        assertEquals(60, first.length());
        assertNotEquals(first, second);
        assertTrue(PasswordUtil.matches("0012-password", first));
        assertTrue(PasswordUtil.matches("0012-password", second));
        assertFalse(PasswordUtil.matches("0013-password", first));
    }

    @Test
    void 拒绝空值无效哈希及MD5摘要() {
        for (String invalid : new String[]{null, "", " ", "not-a-hash", "e10adc3949ba59abbe56e057f20f883e"}) {
            assertFalse(PasswordUtil.matches("123456", invalid));
        }
        for (String invalid : new String[]{null, "", " "}) {
            assertThrows(IllegalArgumentException.class, () -> PasswordUtil.hashPassword(invalid));
        }
        assertFalse(PasswordUtil.matches(null, PasswordUtil.hashPassword("test-password")));
    }

    @Test
    void 拒绝非当前成本的bcrypt哈希() {
        String hash = BCrypt.hashpw("test-password", BCrypt.gensalt(12));
        assertFalse(PasswordUtil.matches("test-password", hash));
    }

    @Test
    void 按UTF8字节限制长度且不会截断超长密码() {
        for (String boundary : new String[]{"a".repeat(72), "中".repeat(24)}) {
            String hash = PasswordUtil.hashPassword(boundary);
            assertTrue(PasswordUtil.matches(boundary, hash));
            assertFalse(PasswordUtil.matches(boundary + "a", hash));
            assertThrows(IllegalArgumentException.class, () -> PasswordUtil.hashPassword(boundary + "a"));
        }
    }
}
