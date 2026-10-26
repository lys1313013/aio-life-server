package top.aiolife.export;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;

import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import top.aiolife.export.dataset.ExportDatasetRegistry;
import top.aiolife.export.mapper.ExportGrantMapper;
import top.aiolife.export.mapper.ExportJobMapper;
import top.aiolife.export.pojo.entity.ExportGrantEntity;
import top.aiolife.export.pojo.req.ExportAuthorizeReq;
import top.aiolife.export.service.ExportAccessGuard;
import top.aiolife.export.service.ExportAuditRecorder;
import top.aiolife.export.service.ExportAuthorizationService;
import top.aiolife.export.service.ExportException;
import top.aiolife.export.service.ExportRateLimiter;
import top.aiolife.export.service.ExportScope;
import top.aiolife.export.service.ExportTime;
import top.aiolife.sso.mapper.UserMapper;
import top.aiolife.sso.pojo.entity.UserEntity;
import top.aiolife.sso.util.PasswordUtil;

import java.util.List;

class ExportAuthorizationTest {
    @Test
    void 导出核验也拒绝字母二级密码但保留登录密码原规则() {
        var users = mock(UserMapper.class);
        var grants = mock(ExportGrantMapper.class);
        var registry = mock(ExportDatasetRegistry.class);
        var service = new ExportAuthorizationService(mock(ExportAccessGuard.class),
                mock(ExportRateLimiter.class), registry, mock(ExportScope.class), grants,
                mock(ExportJobMapper.class), users, mock(ExportAuditRecorder.class));
        var user = new UserEntity();
        user.setSecondaryPasswordSalt("fixture-salt");
        user.setSecondaryPassword(PasswordUtil.encryptPassword("letters-password", "fixture-salt"));
        when(users.selectForAuthUpdate(7L)).thenReturn(user);
        var req = new ExportAuthorizeReq();
        req.setPassword("letters-password");
        req.setPurpose("CREATE");
        req.setDatasetKeys(List.of());
        assertThrows(IllegalArgumentException.class, () -> service.authorize(7L, req));
        verifyNoInteractions(grants);
        // 登录密码仍可含字母；只对二级密码增加数字规则。
        user.setSecondaryPassword(null);
        user.setPasswordSalt("fixture-salt");
        user.setPassword(PasswordUtil.encryptPassword("letters-password", "fixture-salt"));
        when(registry.resolve(anyList())).thenReturn(List.of());
        assertDoesNotThrow(() -> service.authorize(7L, req));
        verify(grants).insert(any(ExportGrantEntity.class));
    }

    @BeforeAll
    static void initTableMetadata() {
        // Mock Mapper 不会初始化实体元数据，Lambda 更新仍需要字段映射。
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        TableInfoHelper.initTableInfo(assistant, ExportGrantEntity.class);
    }

    @Test
    void 核验票据一次消费并绑定会话安全状态用途及范围() {
        var access = mock(ExportAccessGuard.class);
        var grants = mock(ExportGrantMapper.class);
        var scope = mock(ExportScope.class);
        var service =
                new ExportAuthorizationService(
                        access,
                        mock(ExportRateLimiter.class),
                        mock(ExportDatasetRegistry.class),
                        scope,
                        grants,
                        mock(ExportJobMapper.class),
                        mock(UserMapper.class),
                        mock(ExportAuditRecorder.class));
        var user = new UserEntity();
        user.setPassword("hash-a");
        when(access.user(7L)).thenReturn(user);
        when(access.sessionHash()).thenReturn("session-a");
        when(scope.hash(anyList())).thenReturn("scope-a");
        var grant = new ExportGrantEntity();
        grant.setId(1L);
        grant.setPurpose("CREATE");
        grant.setSessionHash("session-a");
        grant.setSecurityHash(ExportAccessGuard.securityHash(user));
        grant.setScopeHash("scope-a");
        grant.setExpiresAt(ExportTime.now().plusMinutes(5));
        when(grants.selectOne(any())).thenReturn(grant);
        when(grants.update(isNull(), any())).thenReturn(1, 0);
        assertSame(grant, service.consume(7L, "token", "CREATE", null, List.of()));
        assertThrows(
                ExportException.class,
                () -> service.consume(7L, "token", "CREATE", null, List.of()));
        clearInvocations(grants);
        assertThrows(
                ExportException.class,
                () -> service.consume(7L, "token", "DOWNLOAD", 1L, List.of()));
        when(access.sessionHash()).thenReturn("session-b");
        assertThrows(
                ExportException.class,
                () -> service.consume(7L, "token", "CREATE", null, List.of()));
        when(access.sessionHash()).thenReturn("session-a");
        user.setPassword("changed");
        assertThrows(
                ExportException.class,
                () -> service.consume(7L, "token", "CREATE", null, List.of()));
        user.setPassword("hash-a");
        when(scope.hash(anyList())).thenReturn("scope-b");
        assertThrows(
                ExportException.class,
                () -> service.consume(7L, "token", "CREATE", null, List.of()));
        verify(grants, never()).update(isNull(), any());
    }
}
