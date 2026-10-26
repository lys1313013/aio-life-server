package top.aiolife.sso.util;

/** 二级密码统一使用至少四位 ASCII 数字，始终按字符串处理以保留前导零。 */
public final class SecondaryPasswordPolicy {
    public static final String PATTERN = "[0-9]{4,}";
    public static final String MESSAGE = "二级密码至少为4位纯数字";

    private SecondaryPasswordPolicy() {
    }

    public static void validate(String password) {
        if (password == null || !password.matches(PATTERN)) {
            throw new IllegalArgumentException(MESSAGE);
        }
    }
}
