package top.aiolife.sso.util;

import cn.hutool.crypto.digest.BCrypt;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/** 登录密码与二级密码统一使用 bcrypt，随机盐和计算成本随哈希一起保存。 */
public final class PasswordUtil {
    private static final int COST = 10;
    private static final int MAX_PASSWORD_BYTES = 72;
    private static final Pattern HASH_PATTERN =
            Pattern.compile("\\$2a\\$" + COST + "\\$[./A-Za-z0-9]{53}");

    private PasswordUtil() {
    }

    public static String hashPassword(String password) {
        validatePassword(password);
        return BCrypt.hashpw(password, BCrypt.gensalt(COST));
    }

    /** 仅验证当前成本的 bcrypt 哈希，使用哈希内的盐，不兼容其他成本或算法。 */
    public static boolean matches(String password, String hash) {
        if (password == null || password.isBlank()
                || password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES
                || hash == null || !HASH_PATTERN.matcher(hash).matches()) {
            return false;
        }
        return BCrypt.checkpw(password, hash);
    }

    /** bcrypt 最多处理 72 字节；显式拒绝超长输入，避免不同密码因截断而匹配。 */
    public static void validatePassword(String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new IllegalArgumentException("密码太长，请缩短后重试");
        }
    }
}
