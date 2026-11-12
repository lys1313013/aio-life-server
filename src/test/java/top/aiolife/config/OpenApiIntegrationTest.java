package top.aiolife.config;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.context.SaTokenContext;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.spring.SaTokenContextForSpringInJakartaServlet;
import cn.dev33.satoken.spring.SaTokenContextRegister;
import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.core.util.PathUtils;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.configuration.SpringDocJavadocConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.webmvc.core.configuration.MultipleOpenApiSupportConfiguration;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import top.aiolife.core.exception.ExceptionHandle;
import top.aiolife.docs.api.DocumentationController;
import top.aiolife.docs.service.DocumentationService;
import top.aiolife.sso.interceptor.ApiKeyInterceptor;
import top.aiolife.sso.interceptor.SecondaryLockInterceptor;
import top.aiolife.sso.interceptor.UserLastActiveInterceptor;
import top.aiolife.sso.service.IApiKeyService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Answers.RETURNS_DEFAULTS;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/** 真实 Controller + springdoc + 认证拦截器；仅模拟业务依赖，不连接数据库、Redis 等外部服务。 */
@WebMvcTest(properties = "springdoc.api-docs.enabled=true")
@ContextConfiguration(classes = OpenApiIntegrationTest.DocumentationApplication.class)
@ImportAutoConfiguration({SpringDocConfiguration.class, SpringDocConfigProperties.class,
        SpringDocJavadocConfiguration.class, SpringDocWebMvcConfiguration.class,
        MultipleOpenApiSupportConfiguration.class})
class OpenApiIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired RequestMappingHandlerMapping mappings;
    @Autowired IApiKeyService apiKeyService;
    private MockedStatic<StpUtil> login;
    private SaTokenContext previousContext;

    @BeforeEach
    void authenticated() {
        previousContext = SaManager.getSaTokenContext();
        SaManager.setSaTokenContext(new SaTokenContextForSpringInJakartaServlet());
        login = mockStatic(StpUtil.class);
        login.when(StpUtil::getLoginIdAsLong).thenReturn(1L);
    }

    @AfterEach
    void closeLogin() {
        login.close();
        SaManager.setSaTokenContext(previousContext);
    }

    @Test
    void LLM接口已移除且MCP接口保留() throws Exception {
        JsonNode document = document("/v3/api-docs");
        JsonNode paths = document.path("paths");
        paths.fieldNames().forEachRemaining(path -> assertFalse(path.startsWith("/llm/"), path));
        assertTrue(paths.path("/mcp/tools/call").has("post"));
        mappings.getHandlerMethods().keySet().forEach(mapping -> {
            mapping.getPatternValues().forEach(path -> assertFalse(path.startsWith("/llm/"), path));
        });
    }

    @Test
    void 文档覆盖所有已加载业务路由且操作标识唯一() throws Exception {
        JsonNode document = document("/v3/api-docs");
        Files.createDirectories(Path.of("target/openapi"));
        Files.writeString(Path.of("target/openapi/openapi.json"), document.toPrettyString());
        assertEquals("3.0.1", document.path("openapi").asText());
        assertEquals("/api", document.path("servers").get(0).path("url").asText().replace("http://localhost", ""));
        var ids = new HashSet<String>();
        var templates = new HashSet<String>();
        document.path("paths").fieldNames().forEachRemaining(path ->
                assertTrue(templates.add(path.replaceAll("\\{[^}]+}", "{}")), "重复路径模板 " + path));
        document.path("paths").forEach(path -> path.forEach(operation ->
                assertTrue(ids.add(operation.path("operationId").asText()), "operationId 必须唯一")));
        validateReferences(document, document);
        assertTrue(ids.size() > 200, "必须扫描真实业务 Controller");
        mappings.getHandlerMethods().forEach((mapping, handler) -> {
            if (!handler.getBeanType().getPackageName().startsWith("top.aiolife.")
                    || handler.hasMethodAnnotation(Hidden.class) || handler.getBeanType().isAnnotationPresent(Hidden.class)) return;
            for (String path : mapping.getPatternValues()) {
                // springdoc 会去掉 Spring 的 catch-all 路径变量前缀 *。
                String documentedPath = PathUtils.parsePath(path.replace("{*", "{"), new LinkedHashMap<>());
                assertTrue(document.path("paths").has(documentedPath), "缺少路由 " + path);
                mapping.getMethodsCondition().getMethods().forEach(method -> assertTrue(
                        document.path("paths").path(documentedPath).has(method.name().toLowerCase(Locale.ROOT)),
                        "缺少操作 " + method + " " + path));
            }
        });
    }

    @Test
    void 文档包含真实泛型响应字符串ID枚举日期格式和JavaDoc() throws Exception {
        JsonNode document = document("/v3/api-docs");
        JsonNode goal = document.path("components").path("schemas").path("GoalVO").path("properties");
        assertEquals("string", goal.path("id").path("type").asText());
        assertFalse(goal.has("userId"));
        assertFalse(goal.has("isDeleted"));
        assertTrue(goal.path("title").path("description").asText().contains("目标标题"));
        assertTrue(goal.path("status").path("enum").toString().contains("in_progress"));
        assertEquals("2026-09-29 10:30:00", goal.path("startDate").path("example").asText());
        assertFalse(goal.path("startDate").has("format"));
        assertEquals("yyyy-MM-dd HH:mm:ss", goal.path("startDate").path("x-date-format").asText());
        JsonNode response = resolve(document, document.at("/paths/~1goals/get/responses/200/content/*~1*/schema"));
        if (response.isMissingNode()) response = resolve(document,
                document.at("/paths/~1goals/get/responses/200/content/application~1json/schema"));
        assertEquals("#/components/schemas/GoalVO", response.at("/properties/data/items/$ref").asText());
        JsonNode pages = document.path("components").path("schemas");
        int pageCount = 0;
        for (JsonNode schema : pages) {
            JsonNode properties = schema.path("properties");
            if (properties.has("items") && properties.has("total")) {
                assertEquals("integer", properties.at("/total/type").asText());
                assertEquals("int64", properties.at("/total/format").asText());
                assertTrue(properties.at("/total/nullable").asBoolean());
                assertFalse(properties.path("total").has("pattern"));
                pageCount++;
            }
        }
        assertTrue(pageCount > 5, "必须覆盖多个业务泛型分页响应");
        assertEquals("integer", response.at("/properties/code/type").asText());
        assertEquals("int32", response.at("/properties/code/format").asText());
        assertTrue(response.at("/properties/code/example").isIntegralNumber());
        assertEquals(0, response.at("/properties/code/example").intValue());
        assertTrue(response.at("/properties/message/description").asText().contains("提示信息"));
        assertFalse(response.path("properties").has("rscode"));
        assertFalse(response.path("properties").has("result"));
        assertEquals("binary", document.at("/paths/~1file~1download~1{id}/get/responses/200/content/*~1*/schema/format").asText());
    }

    @Test
    void 业务数量字段和衣柜分类季节映射描述为整数() throws Exception {
        JsonNode schemas = document("/v3/api-docs").path("components").path("schemas");
        Map<String, List<String>> fields = Map.of(
                "BankCardCoverTemplateVO", List.of("usageCount"),
                "MembershipStatsVO", List.of("activeCount", "expiringCount", "expiredCount", "expiringThisMonthCount"),
                "WardrobeStatsVO", List.of("totalCount"));
        fields.forEach((name, counts) -> counts.forEach(count -> {
            JsonNode schema = schemas.path(name).path("properties").path(count);
            assertEquals("integer", schema.path("type").asText(), name + "." + count);
            assertEquals("int32", schema.path("format").asText());
        }));
        for (String name : List.of("categoryCount", "seasonCount")) {
            JsonNode values = schemas.path("WardrobeStatsVO").path("properties").path(name).path("additionalProperties");
            assertEquals("integer", values.path("type").asText());
            assertEquals("int32", values.path("format").asText());
        }
    }

    @Test
    void 查询参数平铺且数组采用重复键不生成GET请求体() throws Exception {
        JsonNode document = document("/v3/api-docs");
        JsonNode range = document.at("/paths/~1timeRecord~1queryByDateRange/get");
        Map<String, JsonNode> parameters = parameters(range);
        assertTrue(parameters.keySet().containsAll(List.of("page", "pageSize", "startDate", "endDate")), parameters.keySet().toString());
        assertFalse(parameters.keySet().stream().anyMatch(name -> name.startsWith("condition")));
        assertFalse(range.has("requestBody"));
        JsonNode statuses = parameters(document.at("/paths/~1read-record~1page/get")).get("statuses");
        assertNotNull(statuses);
        assertEquals("form", statuses.path("style").asText());
        assertTrue(statuses.path("explode").asBoolean());
        assertTrue(statuses.at("/schema/items/enum").toString().contains("on_hold"));
    }

    @Test
    void 分页只展示统一名称() throws Exception {
        JsonNode document = document("/v3/api-docs");
        for (String path : List.of("/movie/page", "/read-record/page", "/tasks", "/message/page",
                "/message/admin/list", "/system/bank-card-covers/page", "/github/recent-commits")) {
            Map<String, JsonNode> query = parameters(document.path("paths").path(path).path("get"));
            assertTrue(query.containsKey("page"), path + " 缺少 page");
            assertTrue(query.containsKey("pageSize"), path + " 缺少 pageSize");
            for (String alias : List.of("current", "size", "get", "perPage")) {
                assertFalse(query.containsKey(alias), path + ": " + alias);
            }
        }
        Map<String, JsonNode> cursor = parameters(document.path("paths").path("/weread/recent").path("get"));
        assertTrue(cursor.containsKey("cursor"));
        assertTrue(cursor.containsKey("pageSize"));
        assertFalse(cursor.containsKey("size"));
        assertFalse(cursor.containsKey("page"));
    }

    @Test
    void 分组文档及YAML可读取并保留认证要求() throws Exception {
        JsonNode record = document("/v3/api-docs/record");
        assertTrue(record.path("paths").has("/goals"));
        assertFalse(record.path("paths").has("/auth/login"));
        assertEquals("bearer", record.at("/components/securitySchemes/bearerAuth/scheme").asText());
        assertTrue(record.path("security").get(0).has("bearerAuth"));
        JsonNode sso = document("/v3/api-docs/sso");
        assertTrue(sso.at("/paths/~1auth~1login/post/security").isArray());
        assertTrue(sso.at("/paths/~1auth~1login/post/security").isEmpty());
        String yaml = mvc.perform(get("/api/v3/api-docs.yaml").contextPath("/api"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(yaml.contains("openapi: 3.0.1"));
        String groupYaml = mvc.perform(get("/api/v3/api-docs.yaml/record").contextPath("/api"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(groupYaml.contains("/goals:"));
        assertFalse(groupYaml.contains("/auth/login:"));
        for (String group : List.of("system", "wardrobe", "membership", "bankcard", "feedback", "relationship", "mcp")) {
            JsonNode groupDocument = document("/v3/api-docs/" + group);
            assertTrue(groupDocument.has("openapi"));
            assertFalse(groupDocument.path("paths").has("/goals"));
            validateReferences(groupDocument, groupDocument);
        }
    }

    @Test
    void 未登录可读取全部和分组文档但业务接口仍需鉴权() throws Exception {
        login.when(StpUtil::getLoginIdAsLong).thenThrow(new NotLoginException("未登录", "test", null));
        for (String path : List.of("/v3/api-docs", "/v3/api-docs.yaml")) {
            mvc.perform(get("/api" + path).contextPath("/api")).andExpect(status().isOk());
            for (String group : List.of("record", "sso", "system", "wardrobe", "membership", "bankcard",
                    "feedback", "relationship", "mcp")) {
                mvc.perform(get("/api" + path + "/" + group).contextPath("/api"))
                        .andExpect(status().isOk());
            }
        }
        login.verify(StpUtil::getLoginIdAsLong, never());
        for (String path : List.of("/goals", "/timeRecord/queryByDateRange",
                "/v3/api-docs-private", "/v3/api-docs.yaml-private")) {
            mvc.perform(get("/api" + path).contextPath("/api")).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(401))
                    .andExpect(jsonPath("$.message").value("登录已过期，请重新登录"))
                    .andExpect(jsonPath("$.rscode").doesNotExist())
                    .andExpect(jsonPath("$.result").doesNotExist());
        }
    }

    @Test
    void 读取公开文档不校验附带的APIKey() throws Exception {
        login.when(StpUtil::getLoginIdAsLong).thenThrow(new NotLoginException("未登录", "test", null));
        var result = mvc.perform(get("/api/v3/api-docs/record").contextPath("/api")
                        .header("Authorization", "Bearer ak-invalid-document-test"))
                .andExpect(status().isOk()).andReturn();
        assertTrue(mapper.readTree(result.getResponse().getContentAsByteArray()).has("openapi"));
        login.verify(StpUtil::getLoginIdAsLong, never());
        verify(apiKeyService, never()).getByApiKey(anyString());
    }

    @Test
    void 渐进文档匿名可读且目录数量与模块搜索一致() throws Exception {
        login.when(StpUtil::getLoginIdAsLong).thenThrow(new NotLoginException("未登录", "test", null));
        JsonNode catalog = discovery("/docs/catalog");
        assertTrue(catalog.path("operationCount").asInt() > 200);
        var modules = new HashSet<String>();
        catalog.path("modules").forEach(module -> modules.add(module.path("id").asText()));
        assertTrue(modules.containsAll(List.of("record", "sso", "system", "wardrobe", "membership",
                "bankcard", "feedback", "relationship", "mcp")));
        for (JsonNode module : catalog.path("modules")) {
            JsonNode page = discovery("/docs/operations?module=" + module.path("id").asText());
            assertEquals(module.path("operationCount").asInt(), page.path("total").asInt());
            assertFalse(module.path("name").asText().isBlank());
            for (JsonNode item : page.path("items")) {
                assertTrue(item.path("modules").toString().contains(module.path("id").asText()));
                assertFalse(item.has("definition"));
            }
        }
        mvc.perform(get("/api/docs/operations/get_goals").contextPath("/api")
                        .header("Authorization", "Bearer ak-invalid-document-test"))
                .andExpect(status().isOk());
        login.verify(StpUtil::getLoginIdAsLong, never());
        verify(apiKeyService, never()).getByApiKey(anyString());
        for (String path : List.of("/docs/private", "/docs/catalog-private", "/docs/operations-private", "/goals")) {
            mvc.perform(get("/api" + path).contextPath("/api")).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void 搜索支持中文模块分页并校验边界() throws Exception {
        JsonNode goals = discovery("/docs/operations?keyword=目标&module=record&pageSize=100");
        assertTrue(goals.path("total").isIntegralNumber(), "分页总数必须返回 JSON 整数");
        assertTrue(goals.path("items").toString().contains("get_goals"));
        assertEquals(1, discovery("/docs/operations?keyword=get_goals&pageSize=1").path("items").size());
        JsonNode first = discovery("/docs/operations?page=1&pageSize=2");
        JsonNode second = discovery("/docs/operations?page=2&pageSize=2");
        assertEquals(2, first.path("items").size());
        assertEquals(first.path("total"), second.path("total"));
        assertTrue(first.at("/items/1/operationId").asText().compareTo(second.at("/items/0/operationId").asText()) < 0);
        assertTrue(discovery("/docs/operations?page=2147483647&pageSize=100").path("items").isEmpty());
        assertEquals(0, discovery("/docs/operations?keyword=no-such-operation-xyz").path("total").asInt());
        for (String query : List.of("page=0", "page=-1", "page=abc", "page=2147483648", "pageSize=0",
                "pageSize=101", "pageSize=1.5", "module=missing", "keyword=" + "a".repeat(201))) {
            mvc.perform(get("/api/docs/operations?" + query).contextPath("/api")).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/docs/operations/missing_operation").contextPath("/api")).andExpect(status().isNotFound());
    }

    @Test
    void 中文字典搜索覆盖用户及系统字典操作() throws Exception {
        JsonNode results = discovery("/docs/operations?keyword=字典&pageSize=100");
        Map<String, JsonNode> found = new LinkedHashMap<>();
        results.path("items").forEach(item -> found.put(item.path("operationId").asText(), item));
        JsonNode api = document("/v3/api-docs");
        int dictionaryCount = 0;
        for (var path : api.path("paths").properties()) {
            if (List.of("/userDictData/", "/userDictType/", "/sysDictData/", "/sysDictType/")
                    .stream().noneMatch(prefix -> (path.getKey() + "/").startsWith(prefix))) continue;
            for (var method : path.getValue().properties()) {
                String id = method.getValue().path("operationId").asText();
                assertTrue(found.containsKey(id), "中文搜索遗漏字典接口：" + id);
                assertTrue(found.get(id).path("name").asText().contains("字典"), id);
                dictionaryCount++;
            }
        }
        assertTrue(dictionaryCount > 0);
        assertTrue(found.containsKey("get_userDictData_id"), "应可发现业务关联 ID 的名称解析接口");
    }

    @Test
    void 所有公开操作都有简短中文名称且常用业务可搜索() throws Exception {
        JsonNode api = document("/v3/api-docs");
        api.path("paths").properties().forEach(path -> path.getValue().properties().forEach(method -> {
            String summary = method.getValue().path("summary").asText();
            String location = method.getKey() + " " + path.getKey();
            assertTrue(summary.matches(".*[\\p{IsHan}].*"), "缺少中文名称：" + location);
            assertTrue(summary.length() <= 32, "名称应简短：" + location);
            assertFalse(List.of("新增", "删除", "更新", "查询列表", "拖拽排序").contains(summary), location);
        }));
        for (var entry : Map.ofEntries(
                Map.entry("收入", "get_income_query"),
                Map.entry("支出", "get_expense_query"),
                Map.entry("财务", "get_home_sections_finance_calendar"),
                Map.entry("收支", "get_home_sections_finance_calendar"),
                Map.entry("银行卡", "get_bank_cards"),
                Map.entry("会员", "get_membership_list"),
                Map.entry("微信读书", "get_weread_recent"),
                Map.entry("纪念日", "get_anniversaryRecords"),
                Map.entry("运动", "get_exerciseRecord_query"),
                Map.entry("设备", "get_device_query"),
                Map.entry("导出", "get_me_data_exports_catalog"),
                Map.entry("采集", "get_capture_options"),
                Map.entry("定时任务", "get_system_scheduled_jobs"),
                Map.entry("授权", "post_query_access_token")).entrySet()) {
            assertTrue(discovery("/docs/operations?keyword=" + entry.getKey() + "&pageSize=100")
                    .path("items").toString().contains(entry.getValue()), entry.toString());
        }
        assertFalse(api.path("paths").has("/internal/query/access/evaluate"));
        assertFalse(api.path("paths").has("/internal/query/access/check-token"));
    }

    @Test
    void 财务日历和阅读统计的周期模型互不混用() throws Exception {
        JsonNode api = document("/v3/api-docs");
        var bucketRefs = new HashSet<String>();
        for (String schemaName : List.of("FinanceCalendarVO", "FinanceOverviewCalendarVO", "WereadStatsVO")) {
            JsonNode bucketRef = api.path("components").path("schemas").path(schemaName)
                    .path("properties").path("buckets").path("items");
            assertTrue(bucketRefs.add(bucketRef.path("$ref").asText()), "同名周期模型不能相互覆盖");
            JsonNode fields = resolve(api, bucketRef).path("properties");
            if (schemaName.equals("WereadStatsVO")) {
                assertTrue(fields.has("seconds"));
                assertFalse(fields.has("amount"));
            } else {
                for (String field : List.of("amount", "startDate", "endDate", "future")) {
                    assertTrue(fields.has(field), schemaName + " 缺少 " + field);
                }
                assertFalse(fields.has("seconds"));
                if (schemaName.equals("FinanceOverviewCalendarVO")) {
                    for (String field : List.of("income", "expense", "balance")) assertTrue(fields.has(field));
                }
            }
        }
        for (String path : List.of("/home/sections/expense/calendar", "/home/sections/income/calendar",
                "/home/sections/finance/calendar")) {
            Map<String, JsonNode> query = parameters(api.path("paths").path(path).path("get"));
            assertTrue(query.get("date").path("description").asText().contains("yyyy-MM"), path);
            assertTrue(query.get("granularity").path("description").asText().contains("week"), path);
        }
    }

    @Test
    void 匿名图片及协议文档不要求凭据而相邻业务仍需鉴权() throws Exception {
        JsonNode api = document("/v3/api-docs");
        for (String path : List.of("/auth/legal", "/public/images/{id}.{extension}",
                "/membership/provider-icons/{key}", "/cbti/images/{objectName}")) {
            JsonNode security = api.path("paths").path(path).path("get").path("security");
            assertTrue(security.isArray() && security.isEmpty(), path);
        }
        for (String path : List.of("/membership/provider-icons", "/membership/providers", "/auth/info")) {
            assertFalse(api.path("paths").path(path).path("get").path("security").isArray(), path);
        }
        assertTrue(api.at("/security/0").has("bearerAuth"));
    }

    @Test
    void 单接口文档引用完整保留鉴权且不污染原始文档() throws Exception {
        JsonNode original = document("/v3/api-docs");
        JsonNode detail = discovery("/docs/operations/get_goals");
        JsonNode definition = detail.path("definition");
        assertEquals("GET", detail.path("method").asText());
        assertEquals("/goals", detail.path("path").asText());
        assertEquals(1, definition.path("paths").size());
        assertEquals(1, definition.path("paths").path("/goals").size());
        assertEquals("/api", definition.at("/servers/0/url").asText());
        assertEquals(original.at("/paths/~1goals/get"), definition.at("/paths/~1goals/get"));
        assertTrue(definition.at("/security/0").has("bearerAuth"));
        assertTrue(definition.at("/components/securitySchemes").has("bearerAuth"));
        assertTrue(definition.at("/components/schemas").size() < original.at("/components/schemas").size());
        validateReferences(definition, definition);
        JsonNode anonymous = discovery("/docs/operations/post_auth_login").path("definition");
        assertTrue(anonymous.path("security").isEmpty());
        assertFalse(anonymous.at("/components/securitySchemes").has("bearerAuth"));
        validateReferences(anonymous, anonymous);
        assertEquals(original, document("/v3/api-docs"));
        Files.createDirectories(Path.of("target/openapi"));
        Files.writeString(Path.of("target/openapi/goal-detail.json"), detail.toPrettyString());
    }

    @Test
    void 所有已启用操作均可独立解析引用() throws Exception {
        JsonNode page = discovery("/docs/operations?pageSize=100");
        int total = page.path("total").asInt();
        for (int index = 1; (index - 1) * 100 < total; index++) {
            if (index > 1) page = discovery("/docs/operations?pageSize=100&page=" + index);
            for (JsonNode item : page.path("items")) {
                JsonNode definition = discovery("/docs/operations/" + item.path("operationId").asText()).path("definition");
                assertEquals(1, definition.path("paths").size());
                validateReferences(definition, definition);
            }
        }
    }

    @Test
    void 常用中文业务词能发现新增和查询且详情契约准确() throws Exception {
        for (var entry : Map.of("待办", "post_tasks", "闪念", "post_thought", "时迹", "post_timeRecord", "阅读", "get_read_record_page").entrySet()) {
            assertTrue(discovery("/docs/operations?keyword=" + entry.getKey() + "&pageSize=100")
                    .path("items").toString().contains(entry.getValue()));
        }
        JsonNode api = document("/v3/api-docs");
        JsonNode goalType = parameters(api.at("/paths/~1goals/get")).get("type").path("schema");
        assertEquals("integer", goalType.path("type").asText());
        assertEquals(10, goalType.path("enum").size());
        assertEquals(6, goalType.path("enum").get(5).asInt());
        assertTrue(goalType.path("description").asText().contains("6=年度目标"));
        for (String path : List.of("/read-record/page", "/movie/page")) {
            JsonNode operation = api.path("paths").path(path).path("get");
            assertFalse(parameters(operation).get("page").path("required").asBoolean());
            assertFalse(parameters(operation).get("pageSize").path("required").asBoolean());
            assertEquals("array", parameters(operation).get("statuses").path("schema").path("type").asText());
            for (JsonNode param : operation.path("parameters")) {
                assertFalse(List.of("status", "activeOnly").contains(param.path("name").asText()));
            }
        }
        JsonNode schemas = api.at("/components/schemas");
        assertTrue(schemas.path("TimeRecordSaveReq").path("required").toString().contains("startMinute"));
        assertTrue(schemas.path("TimeRecordSaveReq").path("required").toString().contains("endMinute"));
        assertFalse(schemas.path("TimeRecordUpdateReq").path("required").toString().contains("startMinute"));
        assertEquals(1439, schemas.at("/TimeRecordSaveReq/properties/endMinute/maximum").asInt());
        assertTrue(schemas.at("/ReadRecordCreateReq/properties/totalProgress/description").asText().contains("单位"));
        assertTrue(schemas.at("/TaskDetailCreateReq/properties/taskId/description").asText().contains("/tasks"));
        assertTrue(schemas.path("ReadRecordCreateReq").path("required").toString().contains("title"));
    }

    private JsonNode discovery(String path) throws Exception {
        JsonNode response = document(path);
        assertEquals(0, response.path("code").intValue(), response.toString());
        return response.path("data");
    }

    private JsonNode document(String path) throws Exception {
        return mapper.readTree(mvc.perform(get("/api" + path).contextPath("/api"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
    }

    private JsonNode resolve(JsonNode document, JsonNode schema) {
        return schema.has("$ref") ? document.at(schema.path("$ref").asText().substring(1)) : schema;
    }

    private void validateReferences(JsonNode document, JsonNode node) {
        if (node.has("$ref")) {
            String reference = node.path("$ref").asText();
            assertTrue(reference.startsWith("#/"));
            assertFalse(document.at(reference.substring(1)).isMissingNode(), "缺少 Schema " + reference);
        }
        if (node.isContainerNode()) node.forEach(child -> validateReferences(document, child));
    }

    private Map<String, JsonNode> parameters(JsonNode operation) {
        Map<String, JsonNode> result = new LinkedHashMap<>();
        operation.path("parameters").forEach(parameter -> result.put(parameter.path("name").asText(), parameter));
        return result;
    }

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(basePackages = "top.aiolife", useDefaultFilters = false,
            includeFilters = @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = RestController.class),
            excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = DocumentationController.class))
    @Import({DocumentationController.class, DocumentationService.class, OpenApiConfig.class, JsonConfig.class, QueryParamsConfig.class, ExceptionHandle.class,
            SaTokenConfig.class, SaTokenContextRegister.class, ApiKeyInterceptor.class,
            SecondaryLockInterceptor.class, UserLastActiveInterceptor.class})
    static class DocumentationApplication {
        @Bean
        static BeanFactoryPostProcessor controllerDependencies() {
            return factory -> {
                List<Class<?>> dependencies = new ArrayList<>();
                for (String name : factory.getBeanDefinitionNames()) {
                    String className = factory.getBeanDefinition(name).getBeanClassName();
                    if (className == null || !className.startsWith("top.aiolife.")) continue;
                    Class<?> type = ClassUtils.resolveClassName(className, OpenApiIntegrationTest.class.getClassLoader());
                    if (!type.isAnnotationPresent(RestController.class)
                            && !List.of(ApiKeyInterceptor.class, SecondaryLockInterceptor.class, UserLastActiveInterceptor.class).contains(type)) continue;
                    Arrays.stream(type.getDeclaredConstructors()).forEach(constructor -> dependencies.addAll(List.of(constructor.getParameterTypes())));
                    Arrays.stream(type.getDeclaredFields()).filter(field -> field.isAnnotationPresent(Autowired.class))
                            .forEach(field -> dependencies.add(field.getType()));
                }
                for (Class<?> dependency : dependencies) {
                    if (factory.getBeanNamesForType(dependency, true, false).length == 0) {
                        factory.registerSingleton("documentationMock:" + dependency.getName(), mockDependency(dependency));
                    }
                }
            };
        }

        private static Object mockDependency(Class<?> dependency) {
            if (!Filter.class.isAssignableFrom(dependency)) return mock(dependency);
            // MockMvc 会自动注册 Filter 类型的 Controller 依赖；模拟过滤器必须放行请求链。
            // 保留真实认证拦截器，地图代理过滤器本身由专用 HTTP 测试覆盖。
            return mock(dependency, invocation -> {
                if ("doFilter".equals(invocation.getMethod().getName())) {
                    FilterChain chain = invocation.getArgument(2);
                    chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
                    return null;
                }
                return RETURNS_DEFAULTS.answer(invocation);
            });
        }
    }
}
