package top.aiolife.core.cache;

import java.util.*;

/** 二级锁的业务映射。前端菜单路径与后端路由不可直接互换。 */
public final class SecondaryLockPolicy {
    private SecondaryLockPolicy() {}

    private static final Map<String, List<String>> ROUTES = Map.ofEntries(
            Map.entry("/task/todo", List.of("/tasks", "/taskColumn", "/taskDetails")),
            Map.entry("/task/goal", List.of("/goals")),
            Map.entry("/time/time-tracker", List.of("/timeRecord")),
            Map.entry("/time/dashboard", List.of("/timeRecord")),
            Map.entry("/time/my-categories", List.of("/timeTrackerCategory")),
            Map.entry("/time/category-admin", List.of("/timeTrackerCategory/admin")),
            Map.entry("/record/password", List.of("/password")),
            Map.entry("/record/exercise", List.of("/exerciseRecord", "/dashboard/card/EXERCISE")),
            Map.entry("/my-hub/exercise/category-config", List.of("/userDictData", "/userDictType")),
            Map.entry("/record/videoWatch", List.of("/b-video")),
            Map.entry("/record/think", List.of("/thought")),
            Map.entry("/record/memo", List.of("/memo")),
            Map.entry("/record/performance", List.of("/performance")),
            Map.entry("/record/milestone", List.of("/milestones")),
            Map.entry("/record/anniversary", List.of("/anniversaryRecords")),
            Map.entry("/my-hub/honor", List.of("/honorRecords", "/honorCategories")),
            Map.entry("/record/movie", List.of("/movie")),
            Map.entry("/record/read", List.of("/read-record")),
            Map.entry("/record/weread", List.of("/weread", "/dashboard/card/READ")),
            Map.entry("/relationship/graph", List.of("/relationships")),
            Map.entry("/my-hub/feedback", List.of("/feedback")),
            Map.entry("/finance/bank-cards", List.of("/bank-cards")),
            Map.entry("/finance/income", List.of("/income")),
            Map.entry("/finance/expense", List.of("/expense")),
            Map.entry("/finance/import", List.of("/expense/saveBatch")),
            Map.entry("/finance/dashboard", List.of("/income/statistics", "/expense/statistics")),
            Map.entry("/my-hub/device", List.of("/device")),
            Map.entry("/wardrobe", List.of("/wardrobe")),
            Map.entry("/membership", List.of("/membership")),
            Map.entry("/coding/github", List.of("/github", "/dashboard/card/GITHUB")),
            Map.entry("/coding/leetcode", List.of("/leetcode", "/dashboard/card/LEETCODE")),
            Map.entry("/coding/csdn", List.of("/csdn")),
            Map.entry("/message", List.of("/message")),
            Map.entry("/system/user", List.of("/user-center")),
            Map.entry("/system/menu", List.of("/menu/admin")),
            Map.entry("/system/user-dict", List.of("/userDictData/admin", "/userDictType")),
            Map.entry("/system/feedback", List.of("/feedback/admin")),
            Map.entry("/system/config", List.of("/system-config")),
            Map.entry("/system/operation-log", List.of("/system/logs/operation")),
            Map.entry("/system/access-log", List.of("/system/logs/access")),
            Map.entry("/config-management/sysDictType", List.of("/sysDictType")),
            Map.entry("/config-management/sysDictData", List.of("/sysDictData")),
            Map.entry("/mcp/tools", List.of("/mcp")),
            Map.entry("/analytics", List.of("/dashboard")),
            Map.entry("/workspace", List.of("/dashboard")));

    public static String canonicalMenu(String path) {
        if (path == null) return "";
        return switch (path) {
            case "/task-center/todo" -> "/task/todo";
            case "/task-center/goal" -> "/task/goal";
            case "/password-manager" -> "/record/password";
            case "/my-hub/read-record" -> "/record/read";
            case "/relationship" -> "/relationship/graph";
            case "/feedback" -> "/my-hub/feedback";
            default -> path.startsWith("/finance-management/")
                    ? path.replace("/finance-management/", "/finance/")
                    : Set.of("/my-hub/videoWatch", "/my-hub/memo", "/my-hub/performance",
                            "/my-hub/milestone", "/my-hub/anniversary").contains(path)
                    ? path.replace("/my-hub/", "/record/") : path;
        };
    }

    public static boolean under(String path, String prefix) {
        return path != null && (path.equals(prefix) || path.startsWith(prefix + "/"));
    }

    public static Set<String> requestMenus(String path) {
        Set<String> result = new HashSet<>();
        ROUTES.forEach((menu, routes) -> {
            if (routes.stream().anyMatch(route -> under(path, route))) result.add(menu);
        });
        if (under(path, "/timeTrackerCategory/admin")) result.remove("/time/my-categories");
        if (under(path, "/feedback/admin")) result.remove("/my-hub/feedback");
        if (under(path, "/userDictData/admin")) result.remove("/my-hub/exercise/category-config");
        if (path != null && (path.startsWith("/income/statistics") || path.startsWith("/expense/statistics"))) {
            result.add("/finance/dashboard");
        }
        return result;
    }

    public static Set<String> toolMenus(String name) {
        Set<String> menus = new HashSet<>(switch (name) {
            case "task_list", "task_detail_save" -> Set.of("/task/todo");
            case "goal_query", "goal_progress_update" -> Set.of("/task/goal");
            case "time_record_save", "time_record_queryByDateRange" -> Set.of("/time/time-tracker", "/time/dashboard");
            case "time_tracker_category_list" -> Set.of("/time/my-categories");
            case "thought_save" -> Set.of("/record/think");
            case "b_video_query", "b_video_statistics" -> Set.of("/record/videoWatch");
            case "movie_query" -> Set.of("/record/movie");
            case "read_record_query" -> Set.of("/record/read");
            case "anniversary_query" -> Set.of("/record/anniversary");
            case "dashboard_cards" -> Set.of("/analytics", "/workspace", "/record/exercise",
                    "/coding/leetcode", "/coding/github", "/record/weread");
            // 具体媒体业务在解析 URL 后检查，避免锁电影时阻断书籍录入。
            case "douban_wishlist_add" -> Set.<String>of();
            default -> throw new IllegalArgumentException("工具尚未配置二级锁业务映射");
        });
        menus.add("/mcp/tools");
        return menus;
    }
}
