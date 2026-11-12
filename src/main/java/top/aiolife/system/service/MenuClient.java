package top.aiolife.system.service;

/** 菜单展示端；不作为角色权限或二级锁的授权凭据。 */
public enum MenuClient {
    WEB(1), MOBILE(2);

    public static final int ALL = 3;
    private final int bit;

    MenuClient(int bit) { this.bit = bit; }

    public boolean isVisible(Integer visibleClients) {
        return visibleClients != null && (visibleClients & bit) != 0;
    }

    public static MenuClient parse(String client) {
        if ("web".equals(client)) return WEB;
        if ("mobile".equals(client)) return MOBILE;
        throw new IllegalArgumentException("client 只能为 web 或 mobile");
    }
}
