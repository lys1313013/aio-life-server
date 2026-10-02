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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
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
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
                String documentedPath = PathUtils.parsePath(path, new LinkedHashMap<>());
                assertTrue(document.path("paths").has(documentedPath), "缺少路由 " + path);
                mapping.getMethodsCondition().getMethods().forEach(method -> assertTrue(
                        document.path("paths").path(documentedPath).has(method.name().toLowerCase(java.util.Locale.ROOT)),
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
        assertTrue(response.at("/properties/rscode/description").asText().contains("2001"));
        assertEquals("binary", document.at("/paths/~1file~1download~1{id}/get/responses/200/content/*~1*/schema/format").asText());
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
        for (String group : List.of("system", "wardrobe", "membership", "bankcard", "feedback", "relationship", "llm", "mcp")) {
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
                    "feedback", "relationship", "llm", "mcp")) {
                mvc.perform(get("/api" + path + "/" + group).contextPath("/api"))
                        .andExpect(status().isOk());
            }
        }
        login.verify(StpUtil::getLoginIdAsLong, never());
        for (String path : List.of("/goals", "/timeRecord/queryByDateRange",
                "/v3/api-docs-private", "/v3/api-docs.yaml-private")) {
            mvc.perform(get("/api" + path).contextPath("/api")).andExpect(status().isUnauthorized());
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
        assertEquals(10, catalog.path("modules").size());
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
        assertTrue(goals.path("total").isTextual(), "分页总数遵循现有 Long 字符串契约");
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

    private JsonNode discovery(String path) throws Exception {
        JsonNode response = document(path);
        assertEquals("0", response.path("rscode").asText(), response.toString());
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
    @ComponentScan(basePackages = {"top.aiolife.record.api", "top.aiolife.sso.api", "top.aiolife.system.api",
            "top.aiolife.wardrobe.api", "top.aiolife.membership.api", "top.aiolife.bankcard.api",
            "top.aiolife.feedback.api", "top.aiolife.relationship.api", "top.aiolife.llm.api", "top.aiolife.mcp.api"},
            useDefaultFilters = false, includeFilters = @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = RestController.class))
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
                        factory.registerSingleton("documentationMock:" + dependency.getName(), mock(dependency));
                    }
                }
            };
        }
    }
}
