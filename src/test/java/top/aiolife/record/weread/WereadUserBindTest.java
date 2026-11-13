package top.aiolife.record.weread;

import com.alibaba.fastjson2.JSON;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import top.aiolife.core.redis.RedisUtil;
import top.aiolife.record.client.DoubanAccountClient;
import top.aiolife.record.mapper.UserBindMapper;
import top.aiolife.record.pojo.entity.UserBindEntity;
import top.aiolife.record.service.IWereadService;
import top.aiolife.record.service.impl.UserBindServiceImpl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WereadUserBindTest {

    private UserBindServiceImpl service;
    private UserBindMapper mapper;
    private IWereadService weread;

    @BeforeEach
    void setup() {
        mapper = mock(UserBindMapper.class);
        weread = mock(IWereadService.class);
        service = new UserBindServiceImpl(weread, mock(RedisUtil.class), mock(DoubanAccountClient.class));
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
    }

    private UserBindEntity binding() {
        var item = new UserBindEntity();
        item.setId(11L);
        item.setUserId(42L);
        item.setPlatform("weread");
        item.setAccessToken("wrk-test");
        return item;
    }

    @Test
    void list_请求包含Token也不返回微信凭证() {
        var item = binding();
        when(mapper.selectList(any())).thenReturn(List.of(item));
        service.listForUser(42L, true);
        assertNull(item.getAccessToken());
    }

    @Test
    void request_绑定请求日志不含Key() {
        assertFalse(JSON.toJSONString(new Object[]{binding()}).contains("wrk-test"));
        assertFalse(binding().toString().contains("wrk-test"));
    }

    @Test
    void add_绑定入口复用微信读书连接服务() {
        service.createForUser(42L, binding());
        verify(weread).connect(42L, "wrk-test");
        verify(mapper, never()).insert(any(UserBindEntity.class));
    }

    @Test
    void update_留空保留原Key且不回写元数据() {
        when(mapper.selectById(11L)).thenReturn(binding());
        var request = binding();
        request.setAccessToken("");
        request.setMetaFields("{}");
        service.updateForUser(42L, request);
        verifyNoInteractions(weread);
        verify(mapper, never()).updateById(any(UserBindEntity.class));
    }

    @Test
    void update_更换Key复用验证和保存流程() {
        when(mapper.selectById(11L)).thenReturn(binding());
        service.updateForUser(42L, binding());
        verify(weread).connect(42L, "wrk-test");
        verify(mapper, never()).updateById(any(UserBindEntity.class));
    }

    @Test
    void update_不可通过改平台绕过验证() {
        when(mapper.selectById(11L)).thenReturn(binding());
        var request = binding();
        request.setPlatform("github");
        assertThrows(IllegalArgumentException.class, () -> service.updateForUser(42L, request));
        verifyNoInteractions(weread);
        verify(mapper, never()).updateById(any(UserBindEntity.class));
    }

    @Test
    void delete_绑定入口复用断开连接且验证归属() {
        when(mapper.selectById(11L)).thenReturn(binding());
        assertThrows(IllegalArgumentException.class, () -> service.deleteForUser(43L, 11L));
        verifyNoInteractions(weread);

        service.deleteForUser(42L, 11L);
        verify(weread).disconnect(42L);
        verify(mapper, never()).deleteById(11L);
    }

    @Test
    void add_平台大小写不能绕过验证() {
        var request = binding();
        request.setPlatform("WeRead");
        service.createForUser(42L, request);
        verify(weread).connect(42L, "wrk-test");
        verify(mapper, never()).insert(any(UserBindEntity.class));
    }
}
