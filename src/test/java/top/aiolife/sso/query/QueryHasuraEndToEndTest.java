package top.aiolife.sso.query;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import top.aiolife.AioLifeMain;
import top.aiolife.sso.mapper.UserMapper;
import top.aiolife.sso.pojo.entity.UserEntity;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** 显式启动隔离测试栈后执行：真实 AIO Life → Java 网关 → Hasura → MySQL Connector → MySQL。 */
@EnabledIfEnvironmentVariable(named = "AIO_QUERY_E2E", matches = "1")
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(classes = AioLifeMain.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "aio.life.neo4j.enabled=false", "aio.life.query-access.enabled=true",
        "aio.life.query-access.service-key=fixture-service-key-at-least-32-characters"})
class QueryHasuraEndToEndTest {
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired UserMapper users;
    @Autowired JdbcTemplate db;
    @Autowired top.aiolife.sso.service.IApiKeyService apiKeys;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final List<Long> userIds = new ArrayList<>();
    private final List<String> sessions = new ArrayList<>();
    private final List<Long> apiKeyIds = new ArrayList<>();
    private Process gateway;
    private final String prefix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    private String sessionA;
    private String tokenA;
    private String tokenB;

    private String gatewayUrl;

    @BeforeAll void start() throws Exception {
        Path jar = Path.of(System.getenv("AIO_QUERY_TEST_GATEWAY_JAR"));
        assertTrue(Files.isRegularFile(jar), "先构建 aio-life-query jar");
        Path output = Path.of("target/query-e2e"); Files.createDirectories(output);
        int gatewayPort = Integer.parseInt(System.getenv().getOrDefault("AIO_QUERY_TEST_GATEWAY_PORT", "45680"));
        gatewayUrl = "http://127.0.0.1:" + gatewayPort;
        var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-jar", jar.toString());
        var env = process.environment();
        env.put("AIO_QUERY_PORT", Integer.toString(gatewayPort));
        env.put("AIO_QUERY_MANAGEMENT_PORT", "0");
        env.put("AIO_QUERY_ACCESS_URL", "http://127.0.0.1:" + port + "/api/internal/query/access/check-token");
        env.put("AIO_QUERY_HASURA_URL", System.getenv().getOrDefault("AIO_QUERY_TEST_HASURA_URL", "http://127.0.0.1:45682/graphql"));
        env.put("AIO_QUERY_REDIS_PORT", System.getenv().getOrDefault("AIO_LIFE_REDIS_PORT", "6379"));
        env.put("AIO_QUERY_SERVICE_KEY", "fixture-service-key-at-least-32-characters");
        env.put("AIO_QUERY_WEBHOOK_KEY", "fixture-webhook-key-at-least-32-characters");
        env.put("AIO_QUERY_AUDIT_DIRECTORY", output.resolve("audit").toAbsolutePath().toString());
        gateway = process.redirectErrorStream(true).redirectOutput(output.resolve("gateway.log").toFile()).start();
        boolean ready = false;
        for (int attempt = 0; attempt < 80; attempt++) {
            assertTrue(gateway.isAlive(), "网关未启动，检查 target/query-e2e/gateway.log");
            try { if (call("GET", gatewayUrl + "/api/v1/datasets", null, null).statusCode() == 401) { ready = true; break; } }
            catch (Exception ignored) { }
            Thread.sleep(250);
        }
        assertTrue(ready, "网关启动超时");
        long adjacentIds = 8_000_000_000_000_000_000L + (System.currentTimeMillis() % 100_000_000L) * 10;
        for (int index = 0; index < 2; index++) {
            var user = new UserEntity(); user.setUsername("qe2e_" + prefix + index); user.setNickname("隔离查询测试");
            user.setId(adjacentIds + index); user.setRole("user"); user.setIsDeleted(0); users.insert(user); userIds.add(user.getId());
            sessions.add(StpUtil.getStpLogic().createLoginSession(user.getId()));
        }
        sessionA = sessions.getFirst();
        tokenA = sessionA; tokenB = sessions.get(1);
        row("a1", userIds.getFirst(), 30, 0); row("a2", userIds.getFirst(), 60, 0);
        row("deleted", userIds.getFirst(), 777, 1); row("b1", userIds.get(1), 999, 0);
    }

    private void row(String id, long user, int minutes, int deleted) {
        db.update("INSERT INTO time_record(id,user_id,category_id,date,start_time,end_time,duration,is_deleted,title) VALUES(?,?,?,'2026-09-15',0,?,?,?,?)",
                prefix + id, user, 9007199254740993L, minutes, minutes, deleted, "不得暴露的标题");
    }
    private HttpResponse<String> call(String method, String url, String token, Object body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).header("Content-Type", "application/json").header("Accept", "application/json, text/event-stream");
        if (token != null) request.header("Authorization", "Bearer " + token);
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(body)));
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> query(String token, String operation) throws Exception {
        return call("POST", gatewayUrl + "/api/v1/queries/execute", token,
                Map.of("domain", "time", "schemaVersion", "time-record-v1", "policyVersion", "time-record-v1", "operationName", "Q", "query", operation));
    }
    private JsonNode result(String token, String operation) throws Exception {
        var response = query(token, operation); assertEquals(200, response.statusCode(), response.body());
        var body = json.readTree(response.body()); assertEquals("0", body.path("rscode").asText(), response.body());
        return body.path("data").path("result");
    }

    @Test void 单表读取统计隔离及授权撤销闭环() throws Exception {
        var catalog = call("GET", gatewayUrl + "/api/v1/datasets/time_record", tokenA, null);
        assertEquals(200, catalog.statusCode());
        String sdl = json.readTree(catalog.body()).path("data").path("graphqlSdl").asText();
        assertFalse(sdl.contains("userId")); assertFalse(sdl.contains("description:"));
        String where = "where:{date:{_gte:\"2026-09-01\",_lt:\"2026-10-01\"}}";
        String details = "query Q { timeRecords(" + where + ",orderBy:[{id:Asc}]) { id date categoryId durationMinutes } }";
        var a = result(tokenA, details).path("timeRecords");
        assertEquals(2, a.size()); assertEquals(prefix + "a1", a.get(0).path("id").asText());
        assertEquals("9007199254740993", a.get(0).path("categoryId").textValue());
        String categoryWhere = "where:{date:{_gte:\"2026-09-01\",_lt:\"2026-10-01\"},categoryId:{_eq:\"9007199254740993\"}}";
        assertEquals(2, result(tokenA, "query Q { timeRecords(" + categoryWhere + ") { id } }").path("timeRecords").size());
        var nextPage = result(tokenA, "query Q { rows:timeRecords(" + where + ",limit:1,offset:1){ recordId:id } }").path("rows");
        assertEquals(1, nextPage.size()); assertEquals(prefix + "a2", nextPage.get(0).path("recordId").asText());
        var b = result(tokenB, details).path("timeRecords");
        assertEquals(1, b.size()); assertEquals(prefix + "b1", b.get(0).path("id").asText());
        var stats = result(tokenA, "query Q { timeRecordStats(" + where + ") { count durationMinutes { sum } } }").path("timeRecordStats");
        assertEquals("2", stats.path("count").textValue()); assertEquals("90", stats.path("durationMinutes").path("sum").textValue());
        var empty = result(tokenA, "query Q { timeRecordStats(where:{date:{_gte:\"2026-08-01\",_lt:\"2026-09-01\"}}){count durationMinutes{sum}}}").path("timeRecordStats");
        assertEquals("0", empty.path("count").textValue()); assertTrue(empty.path("durationMinutes").path("sum").isNull());
        assertEquals(400, query(tokenA, "query Q { timeRecords(" + where + ") { userId } }").statusCode());
        var directly = call("POST", System.getenv().getOrDefault("AIO_QUERY_TEST_HASURA_URL", "http://127.0.0.1:45682/graphql"), tokenA, Map.of("query", details));
        assertTrue(directly.statusCode() != 200 || json.readTree(directly.body()).has("errors"), "登录凭据不能直接访问 Hasura");
        // MCP 与 REST 共用真实查询链；不使用测试模拟响应。
        assertEquals(401, mcp(null, "tools/list", Map.of()).statusCode());
        var initialized = rpc(tokenA, "initialize", Map.of("protocolVersion", "2025-06-18", "capabilities", Map.of(),
                "clientInfo", Map.of("name", "integration", "version", "1")));
        assertEquals("aio-life-query", initialized.at("/result/serverInfo/name").asText());
        var listed = rpc(tokenA, "tools/list", Map.of()).at("/result/tools"); assertEquals(4, listed.size());
        var described = tool(tokenA, "aio_query_describe_dataset", Map.of("name", "time_record"));
        assertEquals("time-record-v1", described.at("/data/schemaVersion").asText());
        var args = Map.<String, Object>of("domain", "time", "schemaVersion", "time-record-v1", "policyVersion", "time-record-v1",
                "operationName", "Q", "query", details);
        assertTrue(tool(tokenA, "aio_query_validate", args).at("/data/allowed").asBoolean());
        assertEquals(2, tool(tokenA, "aio_query_execute", args).at("/data/result/timeRecords").size());
        assertEquals(prefix + "b1", tool(tokenB, "aio_query_execute", args).at("/data/result/timeRecords/0/id").asText());
        var statsArgs = new java.util.HashMap<>(args); statsArgs.put("query", "query Q { timeRecordStats(" + where + ") { count durationMinutes { sum } } }");
        assertEquals("90", tool(tokenA, "aio_query_execute", statsArgs).at("/data/result/timeRecordStats/durationMinutes/sum").textValue());
        var forged = new java.util.HashMap<>(args); forged.put("userId", userIds.get(1).toString());
        var rejection = rpc(tokenA, "tools/call", Map.of("name", "aio_query_execute", "arguments", forged));
        assertTrue(rejection.has("error") || rejection.at("/result/isError").asBoolean());
        assertEquals(403, mcpWithHeader(tokenA, "X-Hasura-User-Id", userIds.get(1).toString()).statusCode());
        assertEquals(403, mcpWithHeader(tokenA, "Origin", "https://untrusted.example").statusCode());
        var apiKey = apiKeys.generateApiKey(userIds.get(1), "query-mcp-test", 1); apiKeyIds.add(apiKey.getId());
        assertEquals(prefix + "b1", tool(apiKey.getApiKey(), "aio_query_execute", args).at("/data/result/timeRecords/0/id").asText());
        apiKeys.removeById(apiKey.getId());
        assertEquals(401, mcp(apiKey.getApiKey(), "tools/list", Map.of()).statusCode());
        StpUtil.logoutByTokenValue(sessionA);
        assertEquals(401, query(tokenA, details).statusCode());
        assertEquals(401, mcp(tokenA, "tools/list", Map.of()).statusCode());
    }

    private HttpResponse<String> mcp(String token, String method, Object params) throws Exception {
        return call("POST", gatewayUrl + "/mcp", token, Map.of("jsonrpc", "2.0", "id", 1, "method", method, "params", params));
    }
    private JsonNode rpc(String token, String method, Object params) throws Exception {
        var response = mcp(token, method, params); assertEquals(200, response.statusCode(), response.body());
        return json.readTree(response.body());
    }
    private JsonNode tool(String token, String name, Object args) throws Exception {
        var response = rpc(token, "tools/call", Map.of("name", name, "arguments", args));
        assertFalse(response.has("error"), response.toString()); assertFalse(response.at("/result/isError").asBoolean(), response.toString());
        return response.at("/result/structuredContent");
    }
    private HttpResponse<String> mcpWithHeader(String token, String name, String value) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(gatewayUrl + "/mcp")).timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + token).header(name, value).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":{}}"));
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @AfterAll void cleanup() throws Exception {
        if (gateway != null) { gateway.destroy(); if (!gateway.waitFor(10, TimeUnit.SECONDS)) gateway.destroyForcibly(); }
        apiKeyIds.forEach(apiKeys::removeById); sessions.forEach(StpUtil::logoutByTokenValue);
        for (Long id : userIds) { db.update("DELETE FROM time_record WHERE user_id=?", id); db.update("DELETE FROM `user` WHERE id=?", id); }
    }
}
