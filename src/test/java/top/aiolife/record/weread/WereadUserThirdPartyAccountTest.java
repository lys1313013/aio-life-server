package top.aiolife.record.weread;

import com.alibaba.fastjson2.JSON;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import top.aiolife.core.redis.RedisUtil;
import top.aiolife.record.client.ThirdPartyAccountVerificationClient;
import top.aiolife.record.mapper.UserThirdPartyAccountMapper;
import top.aiolife.record.notification.NotificationCryptoService;
import top.aiolife.record.pojo.entity.UserThirdPartyAccountEntity;
import top.aiolife.record.pojo.req.UserThirdPartyAccountVerifyReq;
import top.aiolife.record.service.IWereadService;
import top.aiolife.record.service.impl.UserThirdPartyAccountServiceImpl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WereadUserThirdPartyAccountTest {

    private UserThirdPartyAccountServiceImpl service;
    private UserThirdPartyAccountMapper mapper;
    private IWereadService weread;
    private ThirdPartyAccountVerificationClient verificationClient;
    private NotificationCryptoService crypto;

    @BeforeEach
    void setup() {
        mapper = mock(UserThirdPartyAccountMapper.class);
        weread = mock(IWereadService.class);
        verificationClient = mock(ThirdPartyAccountVerificationClient.class);
        crypto = new NotificationCryptoService("synthetic-test-master-key");
        service = new UserThirdPartyAccountServiceImpl(weread, mock(RedisUtil.class), verificationClient, crypto);
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
    }

    private UserThirdPartyAccountEntity binding() {
        var item = new UserThirdPartyAccountEntity();
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
    void verify_留空使用本人原密钥且新密钥仅用于本次验证() {
        var row = binding();
        row.setAccessTokenCiphertext(crypto.encrypt("user-bind", "wrk-saved", "user_bind:42"));
        when(mapper.selectById(11L)).thenReturn(row);
        var request = new UserThirdPartyAccountVerifyReq();
        request.setId(11L);
        request.setPlatform("weread");
        assertNull(service.verifyForUser(42L, request).accountName());
        verify(verificationClient).verify("weread", null, "wrk-saved");

        request.setAccessToken(" wrk-replacement ");
        service.verifyForUser(42L, request);
        verify(verificationClient).verify("weread", null, "wrk-replacement");
        verify(mapper, times(2)).selectById(11L);
        verifyNoMoreInteractions(mapper);
        verifyNoInteractions(weread);
    }

    @Test
    void verify_拒绝跨用户借用凭证且切换平台不用原凭证() {
        var row = binding();
        row.setAccessTokenCiphertext(crypto.encrypt("user-bind", "wrk-saved", "user_bind:42"));
        when(mapper.selectById(11L)).thenReturn(row);
        var request = new UserThirdPartyAccountVerifyReq();
        request.setId(11L);
        request.setPlatform("github");
        request.setPlatformUsername(" demo-user ");
        assertThrows(IllegalArgumentException.class, () -> service.verifyForUser(43L, request));
        verifyNoInteractions(verificationClient);

        service.verifyForUser(42L, request);
        verify(verificationClient).verify("github", "demo-user", null);
    }

    @Test
    void add_绑定入口复用微信读书连接服务() {
        service.createForUser(42L, binding());
        verify(weread).connect(42L, "wrk-test");
        verify(mapper, never()).insert(any(UserThirdPartyAccountEntity.class));
    }

    @Test
    void update_留空保留原Key且不回写元数据() {
        when(mapper.selectById(11L)).thenReturn(binding());
        var request = binding();
        request.setAccessToken("");
        request.setMetaFields("{}");
        service.updateForUser(42L, request);
        verifyNoInteractions(weread);
        verify(mapper, never()).updateById(any(UserThirdPartyAccountEntity.class));
    }

    @Test
    void update_更换Key复用验证和保存流程() {
        when(mapper.selectById(11L)).thenReturn(binding());
        service.updateForUser(42L, binding());
        verify(weread).connect(42L, "wrk-test");
        verify(mapper, never()).updateById(any(UserThirdPartyAccountEntity.class));
    }

    @Test
    void update_不可通过改平台绕过验证() {
        when(mapper.selectById(11L)).thenReturn(binding());
        var request = binding();
        request.setPlatform("github");
        assertThrows(IllegalArgumentException.class, () -> service.updateForUser(42L, request));
        verifyNoInteractions(weread);
        verify(mapper, never()).updateById(any(UserThirdPartyAccountEntity.class));
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
        verify(mapper, never()).insert(any(UserThirdPartyAccountEntity.class));
    }
}
