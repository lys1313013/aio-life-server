package top.aiolife.database;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import top.aiolife.export.config.ExportProperties;
import top.aiolife.export.dataset.ExportDatasetRegistry;
import top.aiolife.export.mapper.ExportDatasetMapper;
import top.aiolife.export.mapper.ExportJobMapper;
import top.aiolife.export.mapper.SqlDatasetDefinition;
import top.aiolife.export.pojo.entity.ExportGrantEntity;
import top.aiolife.export.pojo.entity.ExportJobEntity;
import top.aiolife.export.pojo.req.ExportCreateReq;
import top.aiolife.export.service.*;
import top.aiolife.sso.pojo.entity.UserEntity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

/** 专用隔离 MySQL 中验证生产 Mapper/事务；不能用生产库执行这些写入夹具。 */
@ActiveProfiles("test")
@SpringBootTest(
        classes = PhysicalTableEntitySelectIntegrationTest.DatabaseMapperTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import({
    ExportJobService.class,
    ExportQuotaService.class,
    ExportDatasetRegistry.class,
    ExportScope.class,
    ExportAuditRecorder.class,
    ExportProperties.class
})
class PersonalDataExportIntegrationTest {
    private static final long USER = 880000000000000101L;
    @Autowired JdbcTemplate sql;
    @Autowired ExportJobService service;
    @Autowired ExportQuotaService quota;
    @Autowired ExportJobMapper jobs;
    @Autowired ExportDatasetMapper datasets;
    @Autowired ExportDatasetRegistry registry;
    @MockitoBean ExportAccessGuard access;
    @MockitoBean ExportAuthorizationService authorization;
    @MockitoSpyBean ExportAuditRecorder audit;

    @BeforeEach
    void setup() {
        cleanup();
        sql.update(
                "INSERT INTO `user` (id,username,nickname,password,password_salt) VALUES"
                        + " (?,?,?,?,?)",
                USER,
                "export-fixture",
                "模拟用户",
                "never-export-this",
                "test-salt");
        var user = new UserEntity();
        user.setId(USER);
        user.setPassword("never-export-this");
        when(access.user(USER)).thenReturn(user);
        when(access.sessionActive(eq(USER), anyString())).thenReturn(true);
        var grant = new ExportGrantEntity();
        grant.setSessionHash("session");
        grant.setSecurityHash(ExportAccessGuard.securityHash(user));
        when(authorization.consume(eq(USER), anyString(), eq("CREATE"), isNull(), anyList()))
                .thenReturn(grant);
    }

    @AfterEach
    void cleanup() {
        reset(audit);
        sql.update("DELETE FROM data_export_audit_event WHERE user_id=?", USER);
        sql.update("DELETE FROM data_export_job WHERE user_id=?", USER);
        sql.update("DELETE FROM `user` WHERE id=?", USER);
        sql.update("DELETE FROM task_detail WHERE id IN (880000000000000201,880000000000000202)");
        sql.update("DELETE FROM task WHERE id IN (880000000000000301,880000000000000302)");
    }

    @Test
    void 审计失败必须回滚任务及额度预占() {
        doThrow(new IllegalStateException("audit unavailable"))
                .when(audit)
                .record(eq(USER), anyLong(), eq("REQUEST_ACCEPTED"), anyString(), anyString());
        var req = new ExportCreateReq();
        req.setDatasetKeys(List.of("task"));
        req.setCatalogVersion(registry.version());
        assertThrows(
                IllegalStateException.class,
                () -> service.create(USER, req, "audit-failure-fixture-key", "token"));
        assertEquals(0, quota.current(USER).used());
        assertEquals(
                0L,
                jobs.selectCount(
                        new LambdaQueryWrapper<ExportJobEntity>()
                                .eq(ExportJobEntity::getUserId, USER)));
    }

    @Test
    void 同一天完成五次之后第六次被月度额度拒绝() {
        for (int i = 0; i < 5; i++) finish(create("monthly-key-00000" + i));
        assertEquals(0, quota.current(USER).remaining());
        ExportException error =
                assertThrows(ExportException.class, () -> create("monthly-key-000009"));
        assertEquals("EXPORT_QUOTA_EXCEEDED", error.getErrorCode());
    }

    @Test
    void 幂等回放不重复扣次且失败退还后允许当天重建() {
        var first = create("idempotent-key-0001");
        var replay = create("idempotent-key-0001");
        assertEquals(first.getId(), replay.getId());
        assertEquals(4, quota.current(USER).remaining());
        var claimed = service.claim();
        service.fail(claimed, "EXPORT_PROCESSING_FAILED");
        assertEquals(5, quota.current(USER).remaining());
        assertNotNull(create("idempotent-key-0002"));
    }

    @Test
    void 多线程申请只保留一个活动任务且不超扣额度() throws Exception {
        for (int i = 0; i < 4; i++) finish(create("concurrent-base-000" + i));
        try (var pool = Executors.newFixedThreadPool(8)) {
            List<Callable<Boolean>> calls =
                    IntStream.range(0, 8)
                            .mapToObj(
                                    i ->
                                            (Callable<Boolean>)
                                                    () -> {
                                                        try {
                                                            create("concurrent-race-000" + i);
                                                            return true;
                                                        } catch (ExportException denied) {
                                                            return false;
                                                        }
                                                    })
                            .toList();
            int accepted = 0;
            for (var result : pool.invokeAll(calls)) if (result.get()) accepted++;
            assertEquals(1, accepted);
            assertEquals(0, quota.current(USER).remaining());
        }
    }

    @Test
    void 所有白名单字段均能在真实MySQL读取且父表越权不泄露() throws Exception {
        List<SqlDatasetDefinition> definitions =
                new ObjectMapper()
                        .readValue(
                                Files.readString(
                                        Path.of("src/main/resources/export/datasets.json")),
                                new TypeReference<>() {});
        for (var definition : definitions)
            assertDoesNotThrow(() -> datasets.read(definition, USER, null, 2), definition.key());
        var profile =
                definitions.stream().filter(d -> d.key().equals("user")).findFirst().orElseThrow();
        var row = datasets.read(profile, USER, null, 2).getFirst();
        assertFalse(row.containsKey("password"));
        assertFalse(row.containsValue("never-export-this"));
        sql.update(
                "INSERT INTO task (id,user_id,content) VALUES"
                        + " (880000000000000301,?,'owned'),(880000000000000302,2,'foreign')",
                USER);
        sql.update(
                "INSERT INTO task_detail (id,task_id,user_id,content) VALUES"
                    + " (880000000000000201,880000000000000301,?,'visible'),(880000000000000202,880000000000000302,?,'must-not-export')",
                USER,
                USER);
        var detail =
                definitions.stream()
                        .filter(d -> d.key().equals("task_detail"))
                        .findFirst()
                        .orElseThrow();
        var rows = datasets.read(detail, USER, null, 2);
        assertEquals(1, rows.size());
        assertEquals("visible", rows.getFirst().get("content"));
    }

    private ExportJobEntity create(String key) {
        var req = new ExportCreateReq();
        req.setDatasetKeys(List.of("memo"));
        req.setCatalogVersion(registry.version());
        return service.create(USER, req, key, "test-grant");
    }

    private void finish(ExportJobEntity created) {
        var claimed = service.claim();
        assertEquals(created.getId(), claimed.getId());
        service.finish(
                claimed, "test/object", "test.xlsx", "application/test", 10, "test-sha", 1, "{}");
    }
}
