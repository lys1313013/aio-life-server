package top.aiolife.record.mcp;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.record.api.*;
import top.aiolife.record.mcp.req.TimeRecordDateRangeMcpReq;
import top.aiolife.record.pojo.entity.TimeRecordEntity;
import top.aiolife.record.pojo.req.TimeRecordReq;
import top.aiolife.record.pojo.vo.TimeRecordDateRangeVO;
import top.aiolife.record.service.ITaskService;
import top.aiolife.record.service.ITimeRecordService;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = RecordMcpE2ETest.MinimalTestApp.class, properties = {
        "spring.main.allow-bean-definition-overriding=true",
        "sa-token.is-read-redis=false",
        "sa-token.is-write-redis=false",
        "server.servlet.context-path=/"
})
public class RecordMcpE2ETest {

    @LocalServerPort
    private int port;

    private McpSyncClient mcpClient;

    @Configuration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            cn.dev33.satoken.dao.SaTokenDaoRedisJackson.class
    })
    @ComponentScan(basePackages = {
            "top.aiolife.mcp", 
            "top.aiolife.record.mcp" 
    }, excludeFilters = {
            @ComponentScan.Filter(type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE, classes = {
                    top.aiolife.mcp.auth.McpSaTokenScope.class
            }),
            @ComponentScan.Filter(type = org.springframework.context.annotation.FilterType.REGEX, pattern = "top\\.aiolife\\.sso\\..*")
    })
    static class MinimalTestApp {
        
        @Bean
        @Primary
        public cn.dev33.satoken.context.SaTokenContext saTokenContext() {
            return new cn.dev33.satoken.context.SaTokenContext() {
                private final cn.dev33.satoken.context.model.SaStorage storage = new cn.dev33.satoken.context.model.SaStorage() {
                    private final Map<String, Object> map = new java.util.concurrent.ConcurrentHashMap<>();
                    @Override public Object getSource() { return map; }
                    @Override public Object get(String key) { return map.get(key); }
                    @Override public cn.dev33.satoken.context.model.SaStorage set(String key, Object value) { map.put(key, value); return this; }
                    @Override public cn.dev33.satoken.context.model.SaStorage delete(String key) { map.remove(key); return this; }
                };
                @Override public cn.dev33.satoken.context.model.SaRequest getRequest() {
                    return new cn.dev33.satoken.context.model.SaRequest() {
                        @Override public Object getSource() { return this; }
                        @Override public String getParam(String name) { return null; }
                        @Override public java.util.Collection<String> getParamNames() { return java.util.Collections.emptyList(); }
                        @Override public java.util.Map<String, String> getParamMap() { return java.util.Collections.emptyMap(); }
                        @Override public String getHeader(String name) { return null; }
                        @Override public String getCookieValue(String name) { return null; }
                        @Override public String getCookieFirstValue(String name) { return null; }
                        @Override public String getCookieLastValue(String name) { return null; }
                        @Override public String getRequestPath() { return "/mcp"; }
                        @Override public String getUrl() { return "/mcp"; }
                        @Override public String getMethod() { return "POST"; }
                        @Override public Object forward(String path) { return null; }
                    };
                }
                @Override public cn.dev33.satoken.context.model.SaResponse getResponse() {
                    return new cn.dev33.satoken.context.model.SaResponse() {
                        @Override public Object getSource() { return this; }
                        @Override public cn.dev33.satoken.context.model.SaResponse setStatus(int sc) { return this; }
                        @Override public cn.dev33.satoken.context.model.SaResponse setHeader(String name, String value) { return this; }
                        @Override public cn.dev33.satoken.context.model.SaResponse addHeader(String name, String value) { return this; }
                        @Override public Object redirect(String url) { return null; }
                    };
                }
                @Override public cn.dev33.satoken.context.model.SaStorage getStorage() { return storage; }
                @Override public boolean matchPath(String pattern, String path) { return true; }
            };
        }

        @Bean
        @Primary
        public cn.dev33.satoken.dao.SaTokenDao saTokenDao() {
            return new cn.dev33.satoken.dao.SaTokenDaoDefaultImpl();
        }

        @Bean
        @Primary
        public TimeRecordController timeRecordController() {
            return new TimeRecordController(null, null, null, null) {
                @Override
                public ApiResponse<List<TimeRecordDateRangeVO>> queryByDateRangeForAI(TimeRecordDateRangeMcpReq req) {
                    TimeRecordDateRangeVO vo = new TimeRecordDateRangeVO();
                    vo.setId("999");
                    return ApiResponse.success(Collections.singletonList(vo));
                }

                @Override
                public ApiResponse<Boolean> save(TimeRecordReq req) {
                    return ApiResponse.success(true);
                }
            };
        }

        @Bean
        @Primary
        public ITimeRecordService timeRecordService() {
            return (ITimeRecordService) java.lang.reflect.Proxy.newProxyInstance(
                    ITimeRecordService.class.getClassLoader(),
                    new Class[]{ITimeRecordService.class},
                    (proxy, method, args) -> {
                        if ("lambdaQuery".equals(method.getName())) {
                            BaseMapper<TimeRecordEntity> dummyMapper = (BaseMapper<TimeRecordEntity>) java.lang.reflect.Proxy.newProxyInstance(
                                    BaseMapper.class.getClassLoader(),
                                    new Class[]{BaseMapper.class},
                                    (mProxy, mMethod, mArgs) -> {
                                        if ("selectList".equals(mMethod.getName()) || "selectOne".equals(mMethod.getName())) {
                                            TimeRecordEntity lastRecord = new TimeRecordEntity();
                                            lastRecord.setEndTime(60);
                                            if ("selectOne".equals(mMethod.getName())) return lastRecord;
                                            return Collections.singletonList(lastRecord);
                                        }
                                        return null;
                                    }
                            );
                            return new LambdaQueryChainWrapper<TimeRecordEntity>(dummyMapper);
                        }
                        return null;
                    }
            );
        }

        @Bean @Primary public ThoughtController thoughtController() { return new ThoughtController(null, null); }
        @Bean @Primary public TimeTrackerCategoryController timeTrackerCategoryController() { return new TimeTrackerCategoryController(null); }
        @Bean @Primary public TaskController taskController() { return new TaskController(null, null, null); }
        @Bean @Primary public TaskDetailController taskDetailController() { return new TaskDetailController(null, null); }
        @Bean @Primary public MovieController movieController() { return new MovieController(null); }
        @Bean @Primary public ReadRecordController readRecordController() { return new ReadRecordController(null); }
        @Bean @Primary public ITaskService taskService() { 
            return (ITaskService) java.lang.reflect.Proxy.newProxyInstance(
                ITaskService.class.getClassLoader(), new Class[]{ITaskService.class}, (p, m, a) -> null); 
        }
    }

    @BeforeEach
    void setUp() {
        cn.dev33.satoken.stp.StpLogic mockLogic = new cn.dev33.satoken.stp.StpLogic("login") {
            @Override
            public long getLoginIdAsLong() {
                return 1L;
            }
            @Override
            public Object getLoginIdDefaultNull() {
                return 1L;
            }
            @Override
            public void checkLogin() {
                // 不做任何检查
            }
        };
        cn.dev33.satoken.stp.StpUtil.setStpLogic(mockLogic);

        String url = "http://localhost:" + port + "/mcp";
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport.builder(url).build();
        mcpClient = McpClient.sync(transport).requestTimeout(Duration.ofSeconds(10)).build();
    }

    @AfterEach
    void tearDown() {
        if (mcpClient != null) {
            mcpClient.closeGracefully();
        }
    }

    @Test
    void testMcpServerInitialization() {
        McpSchema.InitializeResult result = mcpClient.initialize();
        assertNotNull(result);
        assertEquals("aio-life-server-mcp", result.serverInfo().name());

        McpSchema.ListToolsResult toolsResult = mcpClient.listTools();
        assertNotNull(toolsResult);
        assertTrue(toolsResult.tools().size() > 0, "MCP Server 应该注册了工具");
    }

    @Test
    void testCallTimeRecordSaveTool() {
        mcpClient.initialize();

        McpSchema.CallToolResult result = mcpClient.callTool(new McpSchema.CallToolRequest(
                "time_record_save",
                Map.of("date", "2026-05-21", "title", "Test")
        ));

        assertNotNull(result);
        if (result.isError()) {
            System.out.println("Call tool error: " + result.content());
        }
        assertFalse(result.isError());
        McpSchema.TextContent content = (McpSchema.TextContent) result.content().get(0);
        assertTrue(content.text().contains("保存成功") || content.text().contains("true"));
    }
    
    @Test
    void testCallTimeRecordQueryByDateRange() {
        mcpClient.initialize();

        McpSchema.CallToolResult result = mcpClient.callTool(new McpSchema.CallToolRequest(
                "time_record_queryByDateRange",
                Map.of("startDate", "2026-05-01", "endDate", "2026-05-21")
        ));

        assertNotNull(result);
        if (result.isError()) {
            System.err.println("Call tool error in time_record_queryByDateRange: " + result.content());
        }
        assertFalse(result.isError());
        McpSchema.TextContent content = (McpSchema.TextContent) result.content().get(0);
        assertTrue(content.text().contains("999"), "响应中应该包含 mock 数据 ID");
    }
}
