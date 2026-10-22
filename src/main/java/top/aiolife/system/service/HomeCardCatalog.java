package top.aiolife.system.service;


import java.util.List;

/** Stable presentation identifiers shared by all clients; never grant business access. */
public final class HomeCardCatalog {
    private HomeCardCatalog() {}
    public record Definition(String cardKey, String group, String title, String visualKey, boolean defaultEnabled) {}
    private static Definition overview(String key, String title, String visualKey) {
        return new Definition("overview." + key, "overview", title, visualKey, true);
    }
    private static Definition section(String key, String title, String visualKey) {
        return new Definition("section." + key, "section", title, visualKey, true);
    }
    // Keep existing provider / Web section order for users without preferences.
    // New cards are visible by default; explicit user preferences still take precedence.
    public static final List<Definition> ALL = List.of(
        overview("leetcode", "每日一题", "leetcode"),
        overview("github", "GitHub", "github"),
        overview("exercise", "今日运动", "exercise"),
        overview("shanbay", "扇贝单词", "shanbay"),
        overview("read", "今日阅读", "weread"),
        section("time", "时迹", "time"),
        section("links", "快捷导航", "links"),
        section("watched", "待办", "todo"),
        section("thoughts", "闪念", "think"),
        section("goal", "目标", "goal"),
        section("anniversary", "纪念日", "anniversary"),
        section("reading", "阅读", "reading"),
        section("membership", "会员", "membership"),
        section("movie", "观影", "movie"),
        section("exercise", "运动", "exercise"),
        section("github", "GitHub 最近提交", "github"),
        new Definition("section.weread", "section", "微信读书", "weread", true),
        section("memo", "最近笔记", "memo"),
        section("finance", "收支概览", "finance"),
        section("income", "收入", "income"),
        section("expense", "支出", "expense"),
        section("video", "视频续看", "video"),
        section("device", "我的设备", "device")
    );
    public static Definition require(String key) {
        return ALL.stream().filter(item -> item.cardKey().equals(key)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("未知首页卡片，请刷新后重试"));
    }
}
