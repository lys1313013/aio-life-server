package top.aiolife.system.service;

/** 菜单展示端；不作为角色权限或二级锁的授权凭据。 */
public enum MenuClient {
    WEB, MOBILE;

    public static MenuClient parse(String client) {
        if ("web".equals(client)) return WEB;
        if ("mobile".equals(client)) return MOBILE;
        throw new IllegalArgumentException("client 只能为 web 或 mobile");
    }
}
