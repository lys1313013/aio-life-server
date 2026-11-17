package top.aiolife.record.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import top.aiolife.config.MinioConfig;
import top.aiolife.core.util.MinioUtil;
import top.aiolife.record.pojo.entity.AttachmentEntity;
import top.aiolife.record.service.FilePreviewGuard;
import top.aiolife.record.service.IAttachmentService;
import top.aiolife.sso.service.SecondaryLockGuard;
import top.aiolife.system.mapper.StorageObjectMapper;
import top.aiolife.system.pojo.entity.StorageObjectEntity;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static top.aiolife.support.ImageFixtures.image;

class FilePreviewSecurityTest {
    private final MinioUtil minio = mock(MinioUtil.class);
    private final IAttachmentService files = mock(IAttachmentService.class);
    private final FilePreviewGuard guard = mock(FilePreviewGuard.class);
    private final AttachmentEntity file = new AttachmentEntity();
    private final StorageObjectEntity object = new StorageObjectEntity();
    private MockMvc mvc;
    private static final String ID = "a1b2c3d4-1234-4abc-8def-1234567890ab";
    private static final String OBJECT = "1/avatar/fake.png";

    @BeforeEach
    void setup() {
        var config = new MinioConfig();
        config.setBucketName("aiolife");
        var objects=mock(StorageObjectMapper.class);
        object.setId(11L);object.setBucket("aiolife");object.setObjectKey(OBJECT);
        object.setFileSize(68L); object.setContentType("application/octet-stream");
        when(objects.selectById(11L)).thenReturn(object);file.setStorageObjectId(11L);
        mvc = MockMvcBuilders.standaloneSetup(new SysFileController(new top.aiolife.record.service.FileAccessService(
                objects, files, minio, guard, mock(SecondaryLockGuard.class)))).build();
        file.setId("1234567890123456789"); file.setFileKey(ID);
        file.setFileName(OBJECT);
        file.setFileType("text/html");
        file.setIsPublic(1);
        when(files.getByKey(ID)).thenReturn(file);
        when(guard.resolveLoginUserId()).thenReturn(null);
        when(guard.check(file, null)).thenReturn(FilePreviewGuard.AccessDecision.ALLOW);
        when(minio.objectExists("aiolife", OBJECT)).thenReturn(true);
    }

    private String url() { return "/file/preview/" + ID; }

    @Test
    void 数字主键及非v4标识不能进入预览下载入口() throws Exception {
        for (String id : new String[]{"1234567890123456789", "019535d9-3df7-79fb-b466-fa907fa17f9e"}) {
            mvc.perform(get("/file/preview/" + id)).andExpect(status().isNotFound());
            mvc.perform(get("/file/download/" + id)).andExpect(status().isNotFound());
        }
        verify(files, never()).getById(any());
        verify(files, never()).getByKey(any());
        verify(minio, never()).getFile(anyString(), anyString());
    }

    @Test
    void 伪装图片按附件返回() throws Exception {
        byte[] html = "<!doctype html><script>alert(document.domain)</script>".getBytes(StandardCharsets.UTF_8);
        when(minio.getFile("aiolife", OBJECT)).thenAnswer(call -> new ByteArrayInputStream(html));
        // 展示附件的声明类型和后缀不能覆盖物理对象在写入时确认的类型。
        for (String declared : new String[]{"text/html", "image/png", "image/svg+xml", null}) {
            file.setFileType(declared);
            mvc.perform(get(url())).andExpect(status().isOk())
                    .andExpect(content().contentType("application/octet-stream"))
                    .andExpect(header().string("Content-Disposition", startsWith("attachment;")))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                    .andExpect(header().string("Content-Security-Policy", "default-src 'none'; sandbox"))
                    .andExpect(content().bytes(html));
        }
    }

    @Test
    void 使用写入时确认的对象类型预览且不受历史大图像素限制() throws Exception {
        byte[] png = image("png");
        object.setContentType("image/png"); object.setImageWidth(6000); object.setImageHeight(10000);
        object.setFileSize((long) png.length);
        when(minio.getFile("aiolife", OBJECT)).thenReturn(new ByteArrayInputStream(png));
        mvc.perform(get(url())).andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(header().string("Content-Disposition", startsWith("inline;")))
                .andExpect(content().bytes(png));
    }

    @ParameterizedTest
    @ValueSource(strings = {"UNAUTHORIZED", "FORBIDDEN"})
    void 无权限不能读取对象或返回文件内容(String decision) throws Exception {
        when(guard.check(file, null)).thenReturn(FilePreviewGuard.AccessDecision.valueOf(decision));
        mvc.perform(get(url())).andExpect(status().is(decision.equals("UNAUTHORIZED") ? 401 : 403));
        verify(minio, never()).getFile(anyString(), anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"png", "jpeg"})
    void 显式下载图片仍然强制下载(String format) throws Exception {
        byte[] bytes = image(format);
        object.setContentType("image/" + format); object.setFileSize((long) bytes.length);
        when(minio.getFile("aiolife", OBJECT)).thenReturn(new ByteArrayInputStream(bytes));
        mvc.perform(get("/file/download/" + ID)).andExpect(status().isOk())
                .andExpect(content().contentType("application/octet-stream"))
                .andExpect(header().string("Content-Disposition", startsWith("attachment;")))
                .andExpect(content().bytes(bytes));
    }
}
