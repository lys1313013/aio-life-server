package top.aiolife.core.exception;

/** REST 与 MCP 共用的二级锁拒绝结果。 */
public class SecondaryLockRequiredException extends RuntimeException {
    private final String menuPath;
    public SecondaryLockRequiredException(String menuPath) {
        super("需要二级密码验证");
        this.menuPath = menuPath;
    }
    public String getMenuPath() { return menuPath; }
}
