package top.aiolife.record.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import top.aiolife.record.pojo.entity.UserBindEntity;

import java.util.Map;
import java.util.function.Supplier;

/** 小程序编程页面的数据入口：仅代理固定只读操作，账号和凭证取自当前用户绑定。 */
@Service
public class CodingPlatformService {
    private final IUserBindService bindings;
    private final RestClient client;

    @Autowired
    public CodingPlatformService(IUserBindService bindings) {
        this(bindings, builder());
    }

    CodingPlatformService(IUserBindService bindings, RestClient.Builder builder) {
        this.bindings = bindings;
        this.client = builder.build();
    }

    private static RestClient.Builder builder() {
        var factory = new SimpleClientHttpRequestFactory();
        // 总等待短于客户端 15 秒超时，让客户端收到可读的业务错误。
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(10000);
        return RestClient.builder().requestFactory(factory);
    }

    public JsonNode githubCalendar(long userId) {
        var bind = binding(userId, "github");
        if (bind.getAccessToken() == null || bind.getAccessToken().isBlank()) {
            throw new IllegalArgumentException("请在账号绑定中配置 GitHub Token 后重试");
        }
        return exchange("GitHub", () -> client.post().uri("https://api.github.com/graphql")
                .headers(headers -> githubHeaders(headers, bind))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("query", "query($login:String!){user(login:$login){contributionsCollection{contributionCalendar{totalContributions weeks{contributionDays{contributionCount date contributionLevel}}}}}}",
                        "variables", Map.of("login", bind.getPlatformUsername())))
                .retrieve().body(JsonNode.class), false);
    }

    public JsonNode githubRepos(long userId, int page) {
        if (page < 1 || page > 100) throw new IllegalArgumentException("仓库页码无效");
        var bind = binding(userId, "github");
        return exchange("GitHub", () -> client.get()
                .uri("https://api.github.com/users/{username}/repos?sort=pushed&per_page=100&page={page}",
                        bind.getPlatformUsername(), page)
                .headers(headers -> githubHeaders(headers, bind)).retrieve().body(JsonNode.class), true);
    }

    public JsonNode githubRepository(long userId, String name, String section) {
        if (name == null || !name.matches("[A-Za-z0-9_.-]{1,100}") || name.equals(".") || name.equals("..")) {
            throw new IllegalArgumentException("仓库名称无效");
        }
        String suffix = switch (section) {
            case "info" -> "";
            case "contributors" -> "/contributors?per_page=100";
            default -> throw new IllegalArgumentException("仓库查询类型无效");
        };
        var bind = binding(userId, "github");
        return exchange("GitHub", () -> client.get()
                .uri("https://api.github.com/repos/{username}/{name}" + suffix, bind.getPlatformUsername(), name)
                .headers(headers -> githubHeaders(headers, bind)).retrieve().body(JsonNode.class), section.equals("contributors"));
    }

    public JsonNode leetcode(long userId, String section) {
        String query = switch (section) {
            case "profile" -> "query($userSlug:String!){userProfilePublicProfile(userSlug:$userSlug){siteRanking profile{reputation countryName}} userProfileUserQuestionProgress(userSlug:$userSlug){numAcceptedQuestions{difficulty count} numFailedQuestions{difficulty count} numUntouchedQuestions{difficulty count}}}";
            case "contest" -> "query($userSlug:String!){userContestRanking(userSlug:$userSlug){attendedContestsCount rating globalRanking localRanking globalTotalParticipants localTotalParticipants topPercentage}}";
            case "calendar" -> "query($userSlug:String!){userCalendar(userSlug:$userSlug){streak totalActiveDays submissionCalendar activeYears recentStreak}}";
            case "daily" -> "query{todayRecord{date userStatus question{title titleSlug translatedTitle}}}";
            case "recent" -> "query($userSlug:String!){recentACSubmissions(userSlug:$userSlug){submissionId submitTime question{title translatedTitle titleSlug questionFrontendId}}}";
            default -> throw new IllegalArgumentException("LeetCode 查询类型无效");
        };
        var bind = binding(userId, "leetcode");
        String endpoint = section.equals("profile") || section.equals("daily")
                ? "https://leetcode.cn/graphql/" : "https://leetcode.cn/graphql/noj-go/";
        return exchange("LeetCode", () -> client.post().uri(endpoint)
                .header(HttpHeaders.REFERER, "https://leetcode.cn/")
                .header(HttpHeaders.ORIGIN, "https://leetcode.cn")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("query", query, "variables", section.equals("daily") ? Map.of()
                        : Map.of("userSlug", bind.getPlatformUsername())))
                .retrieve().body(JsonNode.class), false).get("data");
    }

    private UserBindEntity binding(long userId, String platform) {
        var bind = bindings.getBindByUserIdAndPlatform(userId, platform);
        if (bind == null || bind.getPlatformUsername() == null || bind.getPlatformUsername().isBlank()) {
            throw new IllegalArgumentException("请先绑定 " + (platform.equals("github") ? "GitHub" : "LeetCode") + " 账号");
        }
        return bind;
    }

    private void githubHeaders(HttpHeaders headers, UserBindEntity bind) {
        headers.set(HttpHeaders.ACCEPT, "application/vnd.github+json");
        headers.set(HttpHeaders.USER_AGENT, "aio-life-server");
        headers.set("X-GitHub-Api-Version", "2022-11-28");
        if (bind.getAccessToken() != null && !bind.getAccessToken().isBlank()) headers.setBearerAuth(bind.getAccessToken());
    }

    private JsonNode exchange(String platform, Supplier<JsonNode> call, boolean array) {
        final JsonNode result;
        try {
            result = call.get();
        } catch (RestClientException e) {
            // 不透传上游正文或请求头；应用 Token 不会发送给第三方。
            throw new IllegalStateException(platform + " 数据请求失败，请稍后重试");
        }
        if (result == null || (array ? !result.isArray() : !result.isObject())
                || result.has("errors") || (!array && platform.equals("LeetCode") && !result.path("data").isObject())) {
            throw new IllegalStateException(platform + " 返回数据异常，请稍后重试");
        }
        stringifyIds(result);
        return result;
    }

    private void stringifyIds(JsonNode node) {
        if (node instanceof ObjectNode object) {
            for (String field : new String[]{"id", "submissionId", "questionFrontendId"}) {
                if (object.path(field).isIntegralNumber()) object.put(field, object.get(field).asText());
            }
        }
        node.forEach(this::stringifyIds);
    }
}
