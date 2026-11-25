package top.aiolife.record.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import top.aiolife.record.service.ICsdnService;
import top.aiolife.record.weread.WereadClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

/** 只读查询各平台，确认公开账号或凭证可用。 */
@Component
public class ThirdPartyAccountVerificationClient {
    private final RestClient client;
    private final DoubanAccountClient douban;
    private final DuolingoClient duolingo;
    private final ICsdnService csdn;
    private final WereadClient weread;

    @Autowired
    public ThirdPartyAccountVerificationClient(DoubanAccountClient douban, DuolingoClient duolingo,
                                               ICsdnService csdn, WereadClient weread) {
        this(douban, duolingo, csdn, weread, createClient());
    }

    ThirdPartyAccountVerificationClient(DoubanAccountClient douban, DuolingoClient duolingo,
                                        ICsdnService csdn, WereadClient weread, RestClient client) {
        this.douban = douban;
        this.duolingo = duolingo;
        this.csdn = csdn;
        this.weread = weread;
        this.client = client;
    }

    private static RestClient createClient() {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(8));
        return RestClient.builder().requestFactory(factory).build();
    }

    public String verify(String platform, String username, String token) {
        return switch (platform) {
            case "github" -> verifyGithub(requireUsername(username, "GitHub", "[A-Za-z0-9_-]{1,100}"), token);
            case "leetcode" -> verifyLeetcode(requireUsername(username, "LeetCode", "[\\p{L}\\p{M}\\p{N}_.-]{1,255}"));
            case "csdn" -> csdn.verifyAccount(requireUsername(username, "CSDN", "[A-Za-z0-9_-]{1,255}"));
            case "shanbay" -> verifyShanbay(requireUsername(username, "扇贝", "[A-Za-z0-9]{1,128}"));
            case "duolingo" -> verifyDuolingo(requireUsername(username, "多邻国", "[\\p{L}\\p{M}\\p{N}_.-]{1,128}"));
            case "douban" -> douban.verify(username).getNickname();
            case "weread" -> {
                if (!StringUtils.hasText(token)) {
                    throw new IllegalArgumentException("请填写微信读书 API Key");
                }
                weread.verifyKey(token);
                yield null;
            }
            default -> throw new IllegalArgumentException("请选择支持的平台");
        };
    }

    private String verifyGithub(String username, String token) {
        boolean authenticated = StringUtils.hasText(token);
        if (authenticated && !token.matches("[A-Za-z0-9_]{1,255}")) {
            throw new IllegalArgumentException("GitHub Token 格式无效");
        }
        final JsonNode result;
        try {
            var request = authenticated
                    ? client.get().uri("https://api.github.com/user").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    : client.get().uri("https://api.github.com/users/{username}", username);
            result = request.header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                    .header(HttpHeaders.USER_AGENT, "aio-life-server")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .retrieve().body(JsonNode.class);
        } catch (RestClientResponseException e) {
            if (authenticated && e.getStatusCode().value() == 401) {
                throw new IllegalArgumentException("GitHub Token 无效或已过期");
            }
            if (!authenticated && e.getStatusCode().value() == 404) {
                throw accountNotFound("GitHub");
            }
            throw unavailable("GitHub");
        } catch (RestClientException e) {
            throw unavailable("GitHub");
        }
        String login = result == null ? "" : result.path("login").asText("");
        if (login.isBlank()) {
            throw unavailable("GitHub");
        }
        if (!login.equalsIgnoreCase(username)) {
            throw new IllegalArgumentException(authenticated
                    ? "GitHub Token 所属账号与填写的用户名不一致" : "GitHub 返回的用户名与填写的用户名不一致");
        }
        return login;
    }

    private String verifyLeetcode(String username) {
        JsonNode result;
        try {
            result = client.post().uri(LeetcodeClient.url)
                    .header(HttpHeaders.REFERER, "https://leetcode.cn/")
                    .header(HttpHeaders.ORIGIN, "https://leetcode.cn")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("query", """
                            query ($userSlug: String!) {
                              userProfilePublicProfile(userSlug: $userSlug) { siteRanking }
                            }
                            """, "variables", Map.of("userSlug", username)))
                    .retrieve().body(JsonNode.class);
        } catch (RestClientException e) {
            throw unavailable("LeetCode");
        }
        if (result == null || result.has("errors")) {
            throw unavailable("LeetCode");
        }
        JsonNode profile = result.path("data").path("userProfilePublicProfile");
        if (profile.isNull()) {
            throw accountNotFound("LeetCode");
        }
        if (!profile.isObject()) {
            throw unavailable("LeetCode");
        }
        return username;
    }

    private String verifyShanbay(String username) {
        JsonNode result;
        try {
            result = client.get().uri("https://www.shanbay.com/api/v1/checkin/user/{id}/?page=1&ipp=1", username)
                    .retrieve().body(JsonNode.class);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                throw accountNotFound("扇贝");
            }
            throw unavailable("扇贝");
        } catch (RestClientException e) {
            throw unavailable("扇贝");
        }
        int status = result == null ? -1 : result.path("status_code").asInt(-1);
        if (status == 404) {
            throw accountNotFound("扇贝");
        }
        if (status != 0 || !result.path("data").isArray()) {
            throw unavailable("扇贝");
        }
        return username;
    }

    private String verifyDuolingo(String username) {
        try {
            if (duolingo.getStreak(username).isEmpty()) {
                throw accountNotFound("多邻国");
            }
        } catch (RestClientException e) {
            throw unavailable("多邻国");
        }
        return username;
    }

    private static String requireUsername(String username, String platform, String pattern) {
        if (username == null || !username.matches(pattern) || username.equals(".") || username.equals("..")) {
            throw new IllegalArgumentException("请填写有效的" + platform + "账号");
        }
        return username;
    }

    private static IllegalArgumentException accountNotFound(String platform) {
        return new IllegalArgumentException("未找到该" + platform + "账号，请检查填写内容");
    }

    private static IllegalStateException unavailable(String platform) {
        return new IllegalStateException(platform + "暂时无法验证，请稍后重试");
    }
}
