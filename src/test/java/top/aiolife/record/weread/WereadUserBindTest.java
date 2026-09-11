package top.aiolife.record.weread;

import cn.dev33.satoken.stp.StpUtil;
import com.alibaba.fastjson2.JSON;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;
import top.aiolife.record.api.UserBindController;
import top.aiolife.record.pojo.entity.UserBindEntity;
import top.aiolife.record.service.IUserBindService;
import top.aiolife.record.service.IWereadService;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WereadUserBindTest {
    private UserBindController controller;
    private IUserBindService binds;
    private IWereadService weread;

    @BeforeEach
    void setup() {
        controller = new UserBindController();
        binds = mock(IUserBindService.class);
        weread = mock(IWereadService.class);
        ReflectionTestUtils.setField(controller, "userBindService", binds);
        ReflectionTestUtils.setField(controller, "wereadService", weread);
    }

    private UserBindEntity binding() {
        var item = new UserBindEntity();
        item.setId(11L); item.setUserId(42L); item.setPlatform("weread"); item.setAccessToken("wrk-test");
        return item;
    }

    @Test
    void list_请求包含Token也不返回微信凭证() {
        var item = binding();
        when(binds.getBindsByUserId(42L)).thenReturn(List.of(item));
        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsLong).thenReturn(42L);
            controller.list(true);
            assertNull(item.getAccessToken());
        }
    }

    @Test
    void request_绑定请求日志不含Key() {
        assertFalse(JSON.toJSONString(new Object[]{binding()}).contains("wrk-test"));
        assertFalse(binding().toString().contains("wrk-test"));
    }

    @Test
    void add_绑定入口复用微信读书连接服务() {
        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsLong).thenReturn(42L);
            controller.add(binding());
            verify(weread).connect("wrk-test");
            verify(binds, never()).save(any());
        }
    }

    @Test
    void update_留空保留原Key且不回写元数据() {
        when(binds.getById(11L)).thenReturn(binding());
        var request = binding(); request.setAccessToken(""); request.setMetaFields("{}");
        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsLong).thenReturn(42L);
            controller.update(request);
            verifyNoInteractions(weread);
            verify(binds, never()).updateById(any());
        }
    }

    @Test
    void update_更换Key复用验证和保存流程() {
        when(binds.getById(11L)).thenReturn(binding());
        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsLong).thenReturn(42L);
            controller.update(binding());
            verify(weread).connect("wrk-test");
            verify(binds, never()).updateById(any());
        }
    }

    @Test
    void update_不可通过改平台绕过验证() {
        when(binds.getById(11L)).thenReturn(binding());
        var request = binding(); request.setPlatform("github");
        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsLong).thenReturn(42L);
            controller.update(request);
            verifyNoInteractions(weread);
            verify(binds, never()).updateById(any());
        }
    }

    @Test
    void delete_绑定入口复用断开连接且验证归属() {
        when(binds.getById(11L)).thenReturn(binding());
        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsLong).thenReturn(43L);
            controller.delete(11L);
            verifyNoInteractions(weread);
            auth.when(StpUtil::getLoginIdAsLong).thenReturn(42L);
            controller.delete(11L);
            verify(weread).disconnect();
            verify(binds, never()).removeById(11L);
        }
    }
    @Test
    void add_平台大小写不能绕过验证() {
        var request = binding(); request.setPlatform("WeRead");
        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsLong).thenReturn(42L);
            controller.add(request);
            verify(weread).connect("wrk-test");
            verify(binds, never()).save(any());
        }
    }

}
