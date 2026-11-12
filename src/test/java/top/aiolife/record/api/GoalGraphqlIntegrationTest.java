package top.aiolife.record.api;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.MockedStatic;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import top.aiolife.record.client.GoalQueryClient;
import top.aiolife.record.config.GoalProgressProperties;
import top.aiolife.record.mapper.IGoalMapper;
import top.aiolife.record.pojo.req.GoalCreateReq;
import top.aiolife.record.pojo.req.GoalUpdateReq;
import top.aiolife.record.pojo.req.GoalProgressPreviewReq;
import top.aiolife.record.pojo.req.GoalProgressRefreshReq;
import top.aiolife.record.pojo.vo.GoalVO;
import top.aiolife.record.service.GoalProgressEvaluator;
import top.aiolife.record.service.GoalProgressService;
import top.aiolife.record.service.impl.GoalServiceImpl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;

/** 只能由 Query 隔离验收脚本提供临时数据库与网关，绝不使用默认业务连接。 */
@SpringJUnitConfig(GoalGraphqlIntegrationTest.Config.class)
@EnabledIfEnvironmentVariable(named = "AIO_GOAL_TEST_JDBC_URL", matches = "jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/aio_life.*")
class GoalGraphqlIntegrationTest {
    @Autowired GoalController controller;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired GoalProgressProperties properties;
    @Autowired IGoalMapper goals;

    @Test void 三个用户自定义查询保存刷新与边界全部通过真实查询链() throws Exception {
        try (MockedStatic<StpUtil> identity = mockStatic(StpUtil.class)) {
            identity.when(StpUtil::getLoginIdAsLong).thenReturn(101L);
            identity.when(StpUtil::getTokenValue).thenReturn("user-a");
            var preserved = goals.selectById(8000L);
            assertEquals("manual", preserved.getProgressMode());
            assertEquals(0, preserved.getCurrentValue().compareTo(new BigDecimal("26")));
            assertEquals(0, preserved.getTargetValue().compareTo(new BigDecimal("40")));
            var pushups = create("pushups");
            var sleep = create("sleep");
            var salary = create("salary");
            assertEquals("graphql", goals.selectById(pushups.getId()).getProgressMode());
            assertNull(pushups.getCurrentValue());
            var refreshed = refresh(pushups, sleep, salary);
            assertEquals(0, refreshed.get(0).getCurrentValue().compareTo(new BigDecimal("26")));
            assertEquals(0, refreshed.get(1).getCurrentValue().compareTo(new BigDecimal("25")));
            assertEquals(0, refreshed.get(2).getCurrentValue().compareTo(new BigDecimal("15000.00")));
            assertTrue(refreshed.stream().allMatch(g -> g.getProgressCheckedAt() != null && g.getProgressError() == null));
            assertEquals(65, preview("pushups").getProgressPercent());
            assertEquals(31, preview("sleep").getProgressPercent());
            assertThrows(org.springframework.dao.DataAccessException.class, () -> jdbc.update("UPDATE goal SET target_value=-1 WHERE id=?", salary.getId()));
            assertThrows(org.springframework.dao.DataAccessException.class, () -> jdbc.update("UPDATE goal SET progress_mode='sql' WHERE id=?", salary.getId()));
            assertThrows(org.springframework.dao.DataAccessException.class, () -> jdbc.update("UPDATE goal SET target_operator='lt' WHERE id=?", salary.getId()));
            assertTrue(preview("salary").isAchieved());
            assertEquals(100, preview("salary").getProgressPercent());

            // 严格超过：80 尚未达标，81 才达标；同日重复、午睡、00:01、删除、其他用户和范围外均已在夹具中。
            jdbc.update("INSERT INTO time_record(id,date,category_id,user_id,start_minute,end_minute) WITH RECURSIVE n AS (SELECT 26 AS v UNION ALL SELECT v+1 FROM n WHERE v<80) SELECT CONCAT('extra-',v),(DATE '2026-01-01' + (v-1)),1,101,0,420 FROM n");
            var equal = preview("sleep");
            assertEquals(0, equal.getCurrentValue().compareTo(new BigDecimal("80")));
            assertFalse(equal.isAchieved()); assertEquals(99, equal.getProgressPercent());
            jdbc.update("INSERT INTO time_record(id,date,category_id,user_id,start_minute,end_minute) VALUES ('extra-81','2026-03-22',1,101,0,420)");
            assertTrue(preview("sleep").isAchieved());
            jdbc.update("UPDATE exercise_record SET exercise_count=40 WHERE id=1");
            assertTrue(preview("pushups").isAchieved());

            // 计算失败保留上次成功值和时间，不当成 0，也不泄露上游错误正文。
            var before = refresh(salary).getFirst();
            URI endpoint = properties.getQueryUrl();
            try {
                properties.setQueryUrl(URI.create("http://127.0.0.1:1/graphql"));
                var failed = refresh(salary).getFirst();
                assertEquals(before.getCurrentValue(), failed.getCurrentValue());
                assertEquals(before.getProgressCheckedAt(), failed.getProgressCheckedAt());
                assertNotNull(failed.getProgressError());
            } finally { properties.setQueryUrl(endpoint); }
            assertNull(refresh(salary).getFirst().getProgressError());
            // 编辑期间完成的旧查询不得覆盖新修订，即使旧查询本身成功。
            var changedEvaluator = org.mockito.Mockito.mock(GoalProgressEvaluator.class);
            org.mockito.Mockito.when(changedEvaluator.evaluate(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString())).thenAnswer(call -> {
                jdbc.update("UPDATE goal SET progress_version=progress_version+1 WHERE id=?", salary.getId());
                var old = new top.aiolife.record.pojo.vo.GoalProgressPreviewVO(); old.setCurrentValue(new BigDecimal("99999")); return old;
            });
            var guarded = new GoalProgressService(goals, changedEvaluator).refresh(101L, List.of(salary.getId()), "user-a").getFirst();
            assertEquals(0, guarded.getCurrentValue().compareTo(new BigDecimal("15000")));
            org.mockito.Mockito.when(changedEvaluator.evaluate(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString())).thenAnswer(call -> {
                jdbc.update("UPDATE goal SET progress_refresh_id='newer-refresh' WHERE id=?", salary.getId());
                var old = new top.aiolife.record.pojo.vo.GoalProgressPreviewVO(); old.setCurrentValue(new BigDecimal("99999")); return old;
            });
            var newer = new GoalProgressService(goals, changedEvaluator).refresh(101L, List.of(salary.getId()), "user-a").getFirst();
            assertEquals(0, newer.getCurrentValue().compareTo(new BigDecimal("15000")));


            // 整批归属校验，猜中其他用户目标 ID 也不能触发查询。
            identity.when(StpUtil::getLoginIdAsLong).thenReturn(102L);
            assertThrows(IllegalArgumentException.class, () -> refresh(pushups));
            identity.when(StpUtil::getLoginIdAsLong).thenReturn(101L);

            // 自动模式拒绝客户端覆盖 currentValue，切回手动时清除查询配置并恢复手填。
            GoalUpdateReq update = new GoalUpdateReq(); update.setId(pushups.getId()); update.setCurrentValue(new BigDecimal("999"));
            var automatic = controller.updateGoal(update).getData();
            assertEquals(0, automatic.getCurrentValue().compareTo(new BigDecimal("26")));
            assertTrue(automatic.getProgressVersion() > pushups.getProgressVersion());
            update.setProgressMode("manual"); update.setCurrentValue(new BigDecimal("12.34"));
            var manual = controller.updateGoal(update).getData();
            assertEquals(0, manual.getCurrentValue().compareTo(new BigDecimal("12.34")));
            assertNull(manual.getProgressQuery());
            assertNull(manual.getProgressCheckedAt());
            assertEquals(0, refresh(manual).getFirst().getCurrentValue().compareTo(new BigDecimal("12.34")));

            // 缺失路径与写操作均拒绝，空聚合字段明确归零。
            var invalid = example("salary", GoalProgressPreviewReq.class); invalid.setProgressValuePath("progress.missing");
            assertThrows(IllegalArgumentException.class, () -> controller.previewProgress(invalid));
            invalid.setProgressQuery("mutation { deleteEverything }");
            assertThrows(IllegalArgumentException.class, () -> controller.previewProgress(invalid));
            var empty = example("pushups", GoalProgressPreviewReq.class); empty.setProgressVariables("{\"exerciseTypeName\":\"不存在的运动类型\"}");
            assertEquals(0, controller.previewProgress(empty).getData().getCurrentValue().signum());
        }
    }

    private GoalVO create(String name) throws Exception { return controller.createGoal(example(name, GoalCreateReq.class)).getData(); }
    private top.aiolife.record.pojo.vo.GoalProgressPreviewVO preview(String name) throws Exception { return controller.previewProgress(example(name, GoalProgressPreviewReq.class)).getData(); }
    private <T> T example(String name, Class<T> type) throws Exception {
        return json.readValue(Files.readString(Path.of(System.getenv("AIO_GOAL_TEST_EXAMPLES"), "goal-" + name + ".json")), type);
    }
    private List<GoalVO> refresh(GoalVO... rows) {
        var req = new GoalProgressRefreshReq(); req.setIds(java.util.Arrays.stream(rows).map(GoalVO::getId).toList());
        var list = controller.refreshProgress(req).getData();
        return java.util.Arrays.stream(rows).map(row -> list.stream().filter(g -> g.getId().equals(row.getId())).findFirst().orElseThrow()).toList();
    }

    @Configuration
    @EnableTransactionManagement
    @Import({GoalController.class, GoalServiceImpl.class, GoalProgressService.class, GoalProgressEvaluator.class, GoalQueryClient.class})
    static class Config {
        @Bean ObjectMapper json() { return new ObjectMapper().findAndRegisterModules(); }
        @Bean DataSource dataSource() { return new DriverManagerDataSource(System.getenv("AIO_GOAL_TEST_JDBC_URL"), "postgres", "fixture-root"); }
        @Bean JdbcTemplate jdbc(DataSource ds) { return new JdbcTemplate(ds); }
        @Bean DataSourceTransactionManager transactionManager(DataSource ds) { return new DataSourceTransactionManager(ds); }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource ds) throws Exception {
            var factory = new MybatisSqlSessionFactoryBean(); factory.setDataSource(ds); return factory.getObject();
        }
        @Bean MapperFactoryBean<IGoalMapper> goals(SqlSessionFactory factory) {
            var mapper = new MapperFactoryBean<>(IGoalMapper.class); mapper.setSqlSessionFactory(factory); return mapper;
        }
        @Bean GoalProgressProperties properties() {
            var props = new GoalProgressProperties(); props.setQueryUrl(URI.create(System.getenv("AIO_GOAL_TEST_QUERY_URL"))); return props;
        }
    }
}
