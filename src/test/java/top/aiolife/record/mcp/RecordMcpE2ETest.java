package top.aiolife.record.mcp;

import cn.dev33.satoken.context.SaTokenContext;
import cn.dev33.satoken.context.model.SaRequest;
import cn.dev33.satoken.context.model.SaResponse;
import cn.dev33.satoken.context.model.SaStorage;
import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import cn.dev33.satoken.dao.SaTokenDaoRedisJackson;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Primary;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.mcp.auth.McpSaTokenScope;
import top.aiolife.record.api.*;
import top.aiolife.record.mapper.IBVideoMapper;
import top.aiolife.record.mcp.req.TimeRecordDateRangeMcpReq;
import top.aiolife.record.pojo.entity.TimeRecordEntity;
import top.aiolife.record.pojo.req.TimeRecordSaveReq;
import top.aiolife.record.pojo.vo.TimeRecordDateRangeVO;
import top.aiolife.record.service.IAnniversaryRecordService;
import top.aiolife.record.service.IGoalService;
import top.aiolife.record.service.IMovieService;
import top.aiolife.record.service.IReadRecordService;
import top.aiolife.record.service.ITaskService;
import top.aiolife.record.service.ITimeRecordService;
import top.aiolife.sso.service.SecondaryLockGuard;

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
    private StpLogic originalStpLogic;

    @Configuration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            SaTokenDaoRedisJackson.class
    })
    @ComponentScan(basePackages = {
            "top.aiolife.mcp",
            "top.aiolife.record.mcp"
    }, excludeFilters = {
            @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {
                    McpSaTokenScope.class
            }),
            @ComponentScan.Filter(type = FilterType.REGEX, pattern = "top\\.aiolife\\.sso\\..*")
    })
    static class MinimalTestApp {
        @Bean
        public SecondaryLockGuard secondaryLockGuard() {
            return Mockito.mock(SecondaryLockGuard.class);
        }

        @Bean
        @Primary
        public SaTokenContext saTokenContext() {
            return new SaTokenContext() {
                private final SaStorage storage = new SaStorage() {
                    private final Map<String, Object> map = new ConcurrentHashMap<>();
                    @Override public Object getSource() { return map; }
                    @Override public Object get(String key) { return map.get(key); }
                    @Override public SaStorage set(String key, Object value) { map.put(key, value); return this; }
                    @Override public SaStorage delete(String key) { map.remove(key); return this; }
                };
                @Override public SaRequest getRequest() {
                    return new SaRequest() {
                        @Override public Object getSource() { return this; }
                        @Override public String getParam(String name) { return null; }
                        @Override public Collection<String> getParamNames() { return Collections.emptyList(); }
                        @Override public Map<String, String> getParamMap() { return Collections.emptyMap(); }
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
                @Override public SaResponse getResponse() {
                    return new SaResponse() {
                        @Override public Object getSource() { return this; }
                        @Override public SaResponse setStatus(int sc) { return this; }
                        @Override public SaResponse setHeader(String name, String value) { return this; }
                        @Override public SaResponse addHeader(String name, String value) { return this; }
                        @Override public Object redirect(String url) { return null; }
                    };
                }
                @Override public SaStorage getStorage() { return storage; }
                @Override public boolean matchPath(String pattern, String path) { return true; }
            };
        }

        @Bean
        @Primary
        public SaTokenDao saTokenDao() {
            return new SaTokenDaoDefaultImpl();
        }

        @Bean
        @Primary
        public TimeRecordController timeRecordController() {
            return new TimeRecordController(Mockito.mock(SecondaryLockGuard.class), null, null, null, null) {
                @Override
                public ApiResponse<List<TimeRecordDateRangeVO>> queryByDateRangeForAI(TimeRecordDateRangeMcpReq req) {
                    TimeRecordDateRangeVO vo = new TimeRecordDateRangeVO();
                    vo.setId("999");
                    return ApiResponse.success(Collections.singletonList(vo));
                }

                @Override
                public ApiResponse<String> save(TimeRecordSaveReq req) {
                    return ApiResponse.success("2099999999999999999");
                }
            };
        }

        @Bean
        @Primary
        public ITimeRecordService timeRecordService() {
            return (ITimeRecordService) Proxy.newProxyInstance(
                    ITimeRecordService.class.getClassLoader(),
                    new Class[]{ITimeRecordService.class},
                    (proxy, method, args) -> {
                        if ("lambdaQuery".equals(method.getName())) {
                            BaseMapper<TimeRecordEntity> dummyMapper = (BaseMapper<TimeRecordEntity>) Proxy.newProxyInstance(
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

        @Bean @Primary public ThoughtController thoughtController() { return new ThoughtController(null, null, null); }
        @Bean @Primary public TimeTrackerCategoryController timeTrackerCategoryController() { return new TimeTrackerCategoryController(null); }
        @Bean @Primary public TaskController taskController() { return new TaskController(null, null, null); }
        @Bean @Primary public TaskDetailController taskDetailController() { return new TaskDetailController(null, null); }
        @Bean @Primary public MovieController movieController() { return new MovieController(null, null); }
        @Bean @Primary public ReadRecordController readRecordController() { return new ReadRecordController(null); }
        @Bean @Primary public ITaskService taskService() {
            return (ITaskService) Proxy.newProxyInstance(
                ITaskService.class.getClassLoader(), new Class[]{ITaskService.class}, (p, m, a) -> null);
        }

        @Bean @Primary public IMovieService movieService() {
            return (IMovieService) Proxy.newProxyInstance(
                IMovieService.class.getClassLoader(), new Class[]{IMovieService.class}, (p, m, a) -> null);
        }

        @Bean @Primary public IReadRecordService readRecordService() {
            return (IReadRecordService) Proxy.newProxyInstance(
                IReadRecordService.class.getClassLoader(), new Class[]{IReadRecordService.class}, (p, m, a) -> null);
        }

        @Bean @Primary public IAnniversaryRecordService anniversaryRecordService() {
            return (IAnniversaryRecordService) Proxy.newProxyInstance(
                IAnniversaryRecordService.class.getClassLoader(),
                new Class[]{IAnniversaryRecordService.class}, (p, m, a) -> null);
        }

        @Bean @Primary public IGoalService goalService() {
            return (IGoalService) Proxy.newProxyInstance(
                IGoalService.class.getClassLoader(),
                new Class[]{IGoalService.class}, (p, m, a) -> null);
        }

        @Bean @Primary public IBVideoMapper bVideoMapper() {
            return (IBVideoMapper) Proxy.newProxyInstance(
                IBVideoMapper.class.getClassLoader(),
                new Class[]{IBVideoMapper.class}, (p, m, a) -> null);
        }
    }

    @BeforeEach
    void setUp() {
        originalStpLogic = StpUtil.getStpLogic();
        StpLogic mockLogic = new StpLogic("login") {
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
        StpUtil.setStpLogic(mockLogic);

        String url = "http://localhost:" + port + "/mcp";
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport.builder(url).build();
        mcpClient = McpClient.sync(transport).requestTimeout(Duration.ofSeconds(10)).build();
    }

    @AfterEach
    void tearDown() {
        try {
            if (mcpClient != null) {
                mcpClient.closeGracefully();
            }
        } finally {
            // StpUtil 是全局状态，不能让 MCP 的测试身份影响后续真实鉴权测试。
            StpUtil.setStpLogic(originalStpLogic);
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
