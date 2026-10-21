package top.aiolife.record.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import top.aiolife.core.cache.SecondaryLockPolicy;
import top.aiolife.record.pojo.entity.UserBindEntity;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class CodingPlatformServiceTest {
    private final IUserBindService bindings = mock(IUserBindService.class);
    private CodingPlatformService service;
    private MockRestServiceServer server;

    @BeforeEach
    void setup() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        service = new CodingPlatformService(bindings, builder);
        var bind = new UserBindEntity();
        bind.setPlatformUsername("fixture-user");
        bind.setAccessToken("fixture-platform-token");
        when(bindings.getBindByUserIdAndPlatform(11L, "github")).thenReturn(bind);
        when(bindings.getBindByUserIdAndPlatform(11L, "leetcode")).thenReturn(bind);
    }

    @Test
    void github_按当前绑定分页并保留大整数ID与数字计数() {
        server.expect(requestTo("https://api.github.com/users/fixture-user/repos?sort=pushed&per_page=100&page=2"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer fixture-platform-token"))
                .andRespond(withSuccess("[{\"id\":9223372036854775806,\"stargazers_count\":4}]", MediaType.APPLICATION_JSON));
        var rows = service.githubRepos(11L, 2);
        assertEquals("9223372036854775806", rows.get(0).get("id").textValue());
        assertTrue(rows.get(0).get("stargazers_count").isNumber());
        server.verify();
    }

    @Test
    void github_贡献日历只使用服务端绑定与Token() {
        server.expect(requestTo("https://api.github.com/graphql"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer fixture-platform-token"))
                .andExpect(jsonPath("$.variables.login").value("fixture-user"))
                .andRespond(withSuccess("{\"data\":{\"user\":{\"contributionsCollection\":{\"contributionCalendar\":{\"weeks\":[],\"totalContributions\":0}}}}}", MediaType.APPLICATION_JSON));
        assertEquals(0, service.githubCalendar(11L).at("/data/user/contributionsCollection/contributionCalendar/totalContributions").asInt());
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"contributors", "info"})
    void github_仓库信息固定在本人账号下(String section) {
        String suffix = section.equals("info") ? "" : "/contributors?per_page=100";
        server.expect(requestTo("https://api.github.com/repos/fixture-user/aio-life" + suffix))
                .andRespond(withSuccess(section.equals("info") ? "{\"parent\":{\"stargazers_count\":9}}" : "[]", MediaType.APPLICATION_JSON));
        assertNotNull(service.githubRepository(11L, "aio-life", section));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"profile", "contest", "calendar", "daily", "recent"})
    void leetcode_固定查询不向第三方转发应用或GitHub凭证(String section) {
        String endpoint = section.equals("profile") || section.equals("daily") ? "" : "noj-go/";
        server.expect(requestTo("https://leetcode.cn/graphql/" + endpoint))
                .andExpect(method(HttpMethod.POST))
                .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
                .andExpect(header(HttpHeaders.REFERER, "https://leetcode.cn/"))
                .andExpect(jsonPath("$.query").isString())
                .andExpect(section.equals("daily") ? jsonPath("$.variables").isEmpty()
                        : jsonPath("$.variables.userSlug").value("fixture-user"))
                .andRespond(withSuccess("{\"data\":{\"recentACSubmissions\":[{\"submissionId\":9223372036854775806,\"submitTime\":123}]}}", MediaType.APPLICATION_JSON));
        var data = service.leetcode(11L, section);
        assertEquals("9223372036854775806", data.at("/recentACSubmissions/0/submissionId").textValue());
        assertTrue(data.at("/recentACSubmissions/0/submitTime").isNumber());
        server.verify();
    }

    @Test
    void 无绑定或非法参数不得发送外部请求() {
        assertThrows(IllegalArgumentException.class, () -> service.githubRepos(22L, 1));
        assertThrows(IllegalArgumentException.class, () -> service.githubRepos(11L, 0));
        assertThrows(IllegalArgumentException.class, () -> service.githubRepository(11L, "../other/repo", "info"));
        assertThrows(IllegalArgumentException.class, () -> service.githubRepository(11L, "repo", "delete"));
        assertThrows(IllegalArgumentException.class, () -> service.leetcode(11L, "https://example.com"));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"errors\":[{\"message\":\"private upstream error\"}]}", "null", "[]", "{\"data\":null}"})
    void GraphQL异常不返回成功且不泄露上游正文(String body) {
        server.expect(requestTo("https://leetcode.cn/graphql/"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        var error = assertThrows(IllegalStateException.class, () -> service.leetcode(11L, "profile"));
        assertEquals("LeetCode 返回数据异常，请稍后重试", error.getMessage());
        server.verify();
    }

    @Test
    void 上游失败提供可重试错误() {
        server.expect(requestTo("https://api.github.com/users/fixture-user/repos?sort=pushed&per_page=100&page=1"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).body("private upstream error"));
        assertEquals("GitHub 数据请求失败，请稍后重试",
                assertThrows(IllegalStateException.class, () -> service.githubRepos(11L, 1)).getMessage());
        server.verify();
    }

    @Test
    void 新接口均保留业务菜单锁() {
        for (String path : new String[]{"/github/repos", "/github/calendar", "/github/repository/info", "/github/repository/contributors"}) {
            assertEquals(Set.of("/coding/github"), SecondaryLockPolicy.requestMenus(path));
        }
        assertEquals(Set.of("/coding/leetcode"), SecondaryLockPolicy.requestMenus("/leetcode/data/profile"));
    }
}
