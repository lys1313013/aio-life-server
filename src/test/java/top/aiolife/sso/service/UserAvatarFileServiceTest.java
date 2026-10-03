package top.aiolife.sso.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import top.aiolife.core.util.MinioUtil;
import top.aiolife.record.mapper.IFileMapper;
import top.aiolife.record.pojo.entity.FileEntity;
import top.aiolife.sso.pojo.req.UpdateUserReq;
import top.aiolife.sso.pojo.req.UserUpdateReq;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserAvatarFileServiceTest {
    static final String ID = "0123456789abcdef0123456789abcdef";
    private final IFileMapper files = mock(IFileMapper.class);
    private final MinioUtil minio = new MinioUtil();
    private final UserAvatarFileService avatars = new UserAvatarFileService(files, minio);

    private FileEntity image() {
        var f = new FileEntity(); f.setId(ID); f.setCreateUser(42L);
        f.setIsDeleted(0); f.setIsPublic(1); f.setBizType("avatar"); f.setFileType("image/png");
        return f;
    }

    @Test void 绑定只接受文件ID并以当前配置生成预览地址() {
        when(files.selectForAvatarBinding(ID)).thenReturn(image());
        when(files.selectById(ID)).thenReturn(image());
        avatars.validateForBinding(42L, ID);
        ReflectionTestUtils.setField(minio, "serveBaseUrl", "https://example.test/api/");
        assertEquals("https://example.test/api/file/preview/" + ID, avatars.publicUrl(42L, ID));
        ReflectionTestUtils.setField(minio, "serveBaseUrl", "https://new.example.test/api");
        assertEquals("https://new.example.test/api/file/preview/" + ID, avatars.publicUrl(42L, ID));
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "deleted", "other-owner", "private", "other-biz", "not-image"})
    void 不允许绑定不可用或非本人头像且查询不暴露地址(String scenario) {
        var f = image();
        switch (scenario) {
            case "missing" -> f = null;
            case "deleted" -> f.setIsDeleted(1);
            case "other-owner" -> f.setCreateUser(99L);
            case "private" -> f.setIsPublic(0);
            case "other-biz" -> f.setBizType("device");
            case "not-image" -> f.setFileType("text/html");
        }
        when(files.selectForAvatarBinding(ID)).thenReturn(f);
        when(files.selectById(ID)).thenReturn(f);
        assertThrows(IllegalArgumentException.class, () -> avatars.validateForBinding(42L, ID));
        assertNull(avatars.publicUrl(42L, ID));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "123", "https://example.test/avatar.png", "../avatar.png"})
    void 不接受旧地址或不合法文件ID(String value) {
        assertThrows(IllegalArgumentException.class, () -> avatars.validateForBinding(42L, value));
        assertNull(avatars.publicUrl(42L, value));
        verifyNoInteractions(files);
    }

    @Test void 清除头像不需要查询文件() {
        avatars.validateForBinding(42L, null);
        assertNull(avatars.publicUrl(42L, null));
        verifyNoInteractions(files);
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
