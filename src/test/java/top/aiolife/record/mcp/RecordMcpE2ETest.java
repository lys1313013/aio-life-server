package top.aiolife.record.mcp;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.context.SaTokenContext;
import cn.dev33.satoken.context.second.SaTokenSecondContext;
import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import cn.dev33.satoken.dao.SaTokenDaoRedisJackson;
import cn.dev33.satoken.spring.SaTokenContextForSpringInJakartaServlet;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
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
import top.aiolife.config.SaTokenConfig;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.mcp.auth.McpSaTokenScope;
import top.aiolife.record.api.*;
import top.aiolife.record.mapper.IBVideoMapper;
import top.aiolife.record.mcp.req.TimeRecordDateRangeMcpReq;
import top.aiolife.record.pojo.entity.TimeRecordEntity;
import top.aiolife.record.pojo.req.TimeRecordSaveReq;
import top.aiolife.record.pojo.req.TaskDetailUpdateReq;
import top.aiolife.record.pojo.vo.TimeRecordDateRangeVO;
import top.aiolife.record.service.IAnniversaryRecordService;
import top.aiolife.record.service.IGoalService;
import top.aiolife.record.service.IMovieService;
import top.aiolife.record.service.IReadRecordService;
import top.aiolife.record.service.ITaskDetail;
import top.aiolife.record.service.ITaskService;
import top.aiolife.record.service.ITimeRecordService;
import top.aiolife.record.service.ITimeTrackerCategoryService;
import top.aiolife.record.service.ThoughtCreationService;
import top.aiolife.sso.interceptor.ApiKeyInterceptor;
import top.aiolife.sso.interceptor.SecondaryLockInterceptor;
import top.aiolife.sso.interceptor.UserLastActiveInterceptor;
import top.aiolife.sso.service.AccountStatusGuard;
import top.aiolife.sso.service.SecondaryLockGuard;
import top.aiolife.sso.util.SecondaryLockSession;

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

    @Autowired
    private ITaskDetail taskDetailService;

    @Autowired
    private SecondaryLockGuard secondaryLockGuard;

    private McpSyncClient mcpClient;
    private StpLogic originalStpLogic;
    private SaTokenContext originalSaTokenContext;
    private SaTokenSecondContext originalSaTokenSecondContext;

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
        public SaTokenDao saTokenDao() {
            return new SaTokenDaoDefaultImpl();
        }

        @Bean
        @Primary
        public ITimeRecordService timeRecordService() {
            var service = Mockito.mock(ITimeRecordService.class);
            Mockito.when(service.queryByDateRangeForAI(Mockito.anyLong(), Mockito.any(), Mockito.any()))
                    .thenAnswer(call -> {
                        TimeRecordDateRangeVO vo = new TimeRecordDateRangeVO();
                        vo.setId("999");
                        vo.setDescription("完成开发与回归验证");
                        return Collections.singletonList(vo);
                    });
            Mockito.when(service.findLatestEndMinute(Mockito.anyLong(), Mockito.any())).thenReturn(60);
            Mockito.when(service.saveTimeRecord(Mockito.anyLong(), Mockito.any()))
                    .thenReturn("2099999999999999999");
            return service;
        }

        @Bean @Primary public ThoughtCreationService thoughtCreationService() {
            return Mockito.mock(ThoughtCreationService.class);
        }

        @Bean @Primary public ITimeTrackerCategoryService timeTrackerCategoryService() {
            return Mockito.mock(ITimeTrackerCategoryService.class);
        }

        @Bean @Primary public ITaskDetail taskDetailService() {
            return Mockito.mock(ITaskDetail.class);
        }

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
        Mockito.reset(taskDetailService, secondaryLockGuard);
        originalStpLogic = StpUtil.getStpLogic();
        // Spring 可能复用测试上下文，但 SaManager 的全局状态会被其他测试替换。
        originalSaTokenContext = SaManager.getSaTokenContext();
        originalSaTokenSecondContext = SaManager.getSaTokenSecondContext();
        SaManager.setSaTokenContext(new SaTokenContextForSpringInJakartaServlet());
        // MCP 工具在工作线程执行，复用生产环境的线程本地上下文。
        new SaTokenConfig(Mockito.mock(ApiKeyInterceptor.class), Mockito.mock(SecondaryLockInterceptor.class),
                Mockito.mock(UserLastActiveInterceptor.class), Mockito.mock(AccountStatusGuard.class)).initSecondContext();
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
            public String getTokenValue() {
                return isSwitch() ? null : "mcp-login-session";
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
            SaManager.setSaTokenContext(originalSaTokenContext);
            SaManager.setSaTokenSecondContext(originalSaTokenSecondContext);
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
        McpSchema.Tool updateTool = toolsResult.tools().stream()
                .filter(tool -> tool.name().equals("task_detail_update"))
                .findFirst().orElseThrow();
        Map<?, ?> idSchema = (Map<?, ?>) updateTool.inputSchema().properties().get("id");
        assertEquals("string", idSchema.get("type"), "明细ID必须使用字符串传输");
    }

    @Test
    void toolSchemaAndValidationRejectMissingContentAndInvalidPriority() {
        mcpClient.initialize();
        var tools = mcpClient.listTools().tools();
        for (String name : List.of("goal_progress_update", "task_detail_save", "task_detail_update")) {
            var tool = tools.stream().filter(value -> value.name().equals(name)).findFirst().orElseThrow();
            String id = name.equals("goal_progress_update") ? "goalId" : name.equals("task_detail_save") ? "taskId" : "id";
            assertTrue(tool.inputSchema().required().contains(id));
            Map<?, ?> schema = (Map<?, ?>) tool.inputSchema().properties().get(id);
            assertEquals("string", schema.get("type"));
            assertEquals("^[1-9][0-9]*$", schema.get("pattern"));
        }
        var thought = mcpClient.callTool(new McpSchema.CallToolRequest("thought_save", Map.of()));
        assertTrue(thought.isError());
        assertTrue(((McpSchema.TextContent) thought.content().getFirst()).text().contains("content"));
        var priority = mcpClient.callTool(new McpSchema.CallToolRequest("task_detail_save",
                Map.of("taskId", "9007199254740993", "content", "模拟待办", "priority", 2)));
        assertTrue(priority.isError());
        assertTrue(((McpSchema.TextContent) priority.content().getFirst()).text().contains("priority"));
        var numericId = mcpClient.callTool(new McpSchema.CallToolRequest("task_detail_update",
                Map.of("id", 9007199254740993L, "isCompleted", 1)));
        assertTrue(numericId.isError());
        Mockito.verifyNoInteractions(taskDetailService);
    }

    @Test
    void testTaskDetailUpdate_协议调用保留大整数和零值() {
        Mockito.when(taskDetailService.updateForUser(Mockito.anyLong(), Mockito.any())).thenReturn(true);
        mcpClient.initialize();
        McpSchema.CallToolResult result = mcpClient.callTool(new McpSchema.CallToolRequest(
                "task_detail_update",
                Map.of("id", "2112457917496135682", "isStarred", 0, "isCompleted", 0, "priority", 20)));

        assertFalse(result.isError());
        assertEquals("true", ((McpSchema.TextContent) result.content().get(0)).text());
        ArgumentCaptor<top.aiolife.record.pojo.entity.TaskDetailEntity> captured =
                ArgumentCaptor.forClass(top.aiolife.record.pojo.entity.TaskDetailEntity.class);
        Mockito.verify(taskDetailService).updateForUser(Mockito.anyLong(), captured.capture());
        assertEquals(2112457917496135682L, captured.getValue().getId());
        assertEquals(0, captured.getValue().getIsStarred());
        assertEquals(0, captured.getValue().getIsCompleted());
        assertEquals(20, captured.getValue().getPriority());
    }

    @Test
    void testTaskDetailUpdate_更新失败返回协议错误() {
        Mockito.when(taskDetailService.updateForUser(Mockito.anyLong(), Mockito.any())).thenReturn(false);
        mcpClient.initialize();
        McpSchema.CallToolResult result = mcpClient.callTool(new McpSchema.CallToolRequest(
                "task_detail_update", Map.of("id", "2112457917496135682", "isStarred", 1)));

        assertTrue(result.isError());
        assertTrue(((McpSchema.TextContent) result.content().get(0)).text().contains("无权限"));
    }

    @Test
    void testCallTimeRecordSaveTool() {
        Mockito.doAnswer(call -> {
            assertEquals(SecondaryLockSession.hash("mcp-login-session"), SecondaryLockSession.currentHash());
            return null;
        }).when(secondaryLockGuard).checkTool("time_record_save");
        mcpClient.initialize();

        McpSchema.CallToolResult result = mcpClient.callTool(new McpSchema.CallToolRequest(
                "time_record_save",
                Map.of("date", "2026-05-21", "title", "Test", "startMinute", 600, "endMinute", 659)
        ));

        assertNotNull(result);
        if (result.isError()) {
            System.out.println("Call tool error: " + result.content());
        }
        assertFalse(result.isError());
        Mockito.verify(secondaryLockGuard).checkTool("time_record_save");
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
        assertTrue(content.text().contains("\"description\":\"完成开发与回归验证\""),
                "MCP 响应中应该包含时迹描述");
    }
}
