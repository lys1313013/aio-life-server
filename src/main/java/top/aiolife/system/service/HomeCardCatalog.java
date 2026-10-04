package top.aiolife.system.service;


import java.util.List;

/** Stable presentation identifiers shared by all clients; never grant business access. */
public final class HomeCardCatalog {
    private HomeCardCatalog() {}
    public record Definition(String cardKey, String group, String title, String icon, boolean defaultEnabled) {}
    private static Definition overview(String key, String title, String icon) {
        return new Definition("overview." + key, "overview", title, icon, true);
    }
    private static Definition section(String key, String title, String icon) {
        return new Definition("section." + key, "section", title, icon, true);
    }
    // Keep existing provider / Web section order for users without preferences.
    // Future additions should explicitly default to disabled.
    public static final List<Definition> ALL = List.of(
        overview("leetcode", "每日一题", "lucide:code"),
        overview("github", "GitHub", "mdi:github"),
        overview("exercise", "今日运动", "mdi:run"),
        overview("shanbay", "扇贝单词", "lucide:leaf"),
        overview("read", "今日阅读", "lucide:book-open"),
        section("time", "时迹", "lucide:clock"),
        section("links", "快捷导航", "lucide:layout-grid"),
        section("watched", "待办", "lucide:list-checks"),
        section("thoughts", "闪念", "lucide:lightbulb"),
        section("goal", "目标", "lucide:crosshair"),
        section("anniversary", "纪念日", "mdi:calendar-heart"),
        section("reading", "阅读", "lucide:book-open"),
        section("membership", "会员", "lucide:crown"),
        section("movie", "观影", "lucide:clapperboard"),
        section("exercise", "运动", "mdi:run"),
        section("github", "GitHub 最近提交", "mdi:github")
    );
    public static Definition require(String key) {
        return ALL.stream().filter(item -> item.cardKey().equals(key)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("未知首页卡片，请刷新后重试"));
    }
}
