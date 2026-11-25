package top.aiolife.record.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import top.aiolife.record.service.ICsdnService;
import top.aiolife.record.weread.WereadClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ThirdPartyAccountVerificationClientTest {
    private MockRestServiceServer server;
    private ThirdPartyAccountVerificationClient verifier;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        verifier = new ThirdPartyAccountVerificationClient(mock(DoubanAccountClient.class),
                mock(DuolingoClient.class), mock(ICsdnService.class), mock(WereadClient.class), builder.build());
    }

    @Test
    void verifyGithub_可查询公开账号并校验凭证归属() {
        server.expect(requestTo("https://api.github.com/users/demo-user"))
                .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
                .andRespond(withSuccess("{\"login\":\"Demo-User\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.github.com/user"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer ghp_fixture"))
                .andRespond(withSuccess("{\"login\":\"another-user\"}", MediaType.APPLICATION_JSON));

        assertEquals("Demo-User", verifier.verify("github", "demo-user", null));
        assertEquals("GitHub Token 所属账号与填写的用户名不一致", assertThrows(IllegalArgumentException.class,
                () -> verifier.verify("github", "demo-user", "ghp_fixture")).getMessage());
        server.verify();
    }

    @Test
    void verifyGithub_失效凭证不返回上游正文() {
        server.expect(requestTo("https://api.github.com/user"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("ghp_fixture"));
        assertEquals("GitHub Token 无效或已过期", assertThrows(IllegalArgumentException.class,
                () -> verifier.verify("github", "demo-user", "ghp_fixture")).getMessage());
        server.verify();
    }

    @Test
    void verifyLeetcode_公开资料为空时拒绝验证() {
        server.expect(requestTo("https://leetcode.cn/graphql/"))
                .andExpect(jsonPath("$.variables.userSlug").value("missing-user"))
                .andRespond(withSuccess("{\"data\":{\"userProfilePublicProfile\":null}}", MediaType.APPLICATION_JSON));
        assertThrows(IllegalArgumentException.class, () -> verifier.verify("leetcode", "missing-user", null));
        server.verify();
    }

    @Test
    void verifyShanbay_无打卡记录可通过但业务404拒绝验证() {
        server.expect(requestTo("https://www.shanbay.com/api/v1/checkin/user/demo/?page=1&ipp=1"))
                .andRespond(withSuccess("{\"status_code\":0,\"data\":[]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://www.shanbay.com/api/v1/checkin/user/unknown/?page=1&ipp=1"))
                .andRespond(withSuccess("{\"status_code\":404,\"data\":{}}", MediaType.APPLICATION_JSON));

        assertEquals("demo", verifier.verify("shanbay", "demo", null));
        assertThrows(IllegalArgumentException.class, () -> verifier.verify("shanbay", "unknown", null));
        server.verify();
    }

    @Test
    void verifyWeread_空书架可验证且拒绝异常格式() throws Exception {
        var weread = spy(new WereadClient(RestClient.builder()));
        var json = new ObjectMapper();
        doReturn(json.readTree("{\"books\":[]}")).when(weread).call("wrk-empty", "/shelf/sync", Map.of());
        doReturn(json.readTree("{}")).when(weread).call("wrk-malformed", "/shelf/sync", Map.of());

        weread.verifyKey("wrk-empty");
        assertThrows(IllegalStateException.class, () -> weread.verifyKey("wrk-malformed"));
    }
}
