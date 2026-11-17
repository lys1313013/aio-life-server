package top.aiolife.sso.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import top.aiolife.core.util.MinioUtil;
import top.aiolife.record.mapper.IAttachmentMapper;
import top.aiolife.record.pojo.entity.AttachmentEntity;
import top.aiolife.sso.pojo.req.UpdateUserReq;
import top.aiolife.sso.pojo.req.UserUpdateReq;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

class UserAvatarFileServiceTest {
    static final String ID = "a1b2c3d4-1234-4abc-8def-1234567890ab";
    private final IAttachmentMapper files = mock(IAttachmentMapper.class);
    private final MinioUtil minio = new MinioUtil();
    private final UserAvatarFileService avatars = new UserAvatarFileService(files, minio,org.mockito.Mockito.mock(top.aiolife.record.service.VideoCoverStorage.class));

    private AttachmentEntity image() {
        var f = new AttachmentEntity(); f.setId("1234567890123456789"); f.setFileKey(ID); f.setUserId(42L); f.setCreateUser(42L); f.setStorageObjectId(11L); f.setHashValue("a".repeat(64));
        f.setIsDeleted(0); f.setIsPublic(1); f.setBizType("avatar"); f.setFileType("image/png");
        return f;
    }

    @Test void 当前头像按业务关联读取并以当前配置生成预览地址() {
        when(files.selectOne(any())).thenReturn(image());
        when(files.selectWithObject("1234567890123456789")).thenReturn(image());
        ReflectionTestUtils.setField(minio, "serveBaseUrl", "https://example.test/api/");
        assertEquals(new UserAvatarFileService.Avatar(ID, "https://example.test/api/f/" + "a".repeat(64) + ".png"), avatars.current(42L));
        ReflectionTestUtils.setField(minio, "serveBaseUrl", "https://new.example.test/api");
        assertEquals("https://new.example.test/api/f/" + "a".repeat(64) + ".png", avatars.current(42L).url());
    }

    @Test void 无绑定时返回空头像() {
        when(files.selectOne(any())).thenReturn(null);
        assertEquals(new UserAvatarFileService.Avatar(null, null), avatars.current(42L));
    }

    @Test void 绑定只接受文件ID并锁定文件() {
        when(files.selectForBindingByKey(ID)).thenReturn(image());
        avatars.validateForBinding(42L, ID);
        verify(files).selectForBindingByKey(ID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "deleted", "other-owner", "other-biz", "not-image", "bound-other"})
    void 不允许绑定不可用或非本人头像(String scenario) {
        var f = image();
        switch (scenario) {
            case "missing" -> f = null;
            case "deleted" -> f.setIsDeleted(1);
            case "other-owner" -> f.setUserId(99L);
            case "private" -> f.setIsPublic(0);
            case "other-biz" -> f.setBizType("device");
            case "not-image" -> f.setFileType("text/html");
            case "bound-other" -> f.setBizId(99L);
        }
        when(files.selectForBindingByKey(ID)).thenReturn(f);
        assertThrows(IllegalArgumentException.class, () -> avatars.validateForBinding(42L, ID));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "https://example.test/avatar.png", "../avatar.png"})
    void 不接受旧地址或不合法文件ID(String value) {
        assertThrows(IllegalArgumentException.class, () -> avatars.validateForBinding(42L, value));
        verifyNoInteractions(files);
    }

    @Test void 清除头像不需要查询文件() {
        avatars.validateForBinding(42L, null);
        verifyNoInteractions(files);
    }

    @Test void 替换只解除旧关联并绑定新文件() {
        when(files.selectForBindingByKey(ID)).thenReturn(image());
        avatars.replace(42L, ID);
        verify(files, times(2)).update(isNull(), any());
    }

    @Test void 显式清空只解除关联不查询文件() {
        avatars.replace(42L, null);
        verify(files).update(isNull(), any());
        verify(files, never()).selectForBindingByKey(any());
    }

    @Test void 请求解析区分缺省与显式null且标记不进入JSON() throws Exception {
        var json = new ObjectMapper();
        assertFalse(json.readValue("{}", UpdateUserReq.class).isAvatarFileIdSpecified());
        var clear = json.readValue("{\"avatarFileId\":null}", UpdateUserReq.class);
        assertTrue(clear.isAvatarFileIdSpecified()); assertNull(clear.getAvatarFileId());
        assertFalse(json.writeValueAsString(clear).contains("Specified"));
        assertFalse(json.readValue("{}", UserUpdateReq.class).isAvatarFileIdSpecified());
        assertTrue(json.readValue("{\"id\":42,\"avatarFileId\":null}", UserUpdateReq.class).isAvatarFileIdSpecified());
    }
}
