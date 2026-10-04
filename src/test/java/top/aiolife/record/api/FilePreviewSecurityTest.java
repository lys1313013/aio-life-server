package top.aiolife.record.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import top.aiolife.config.CbtiConfig;
import top.aiolife.config.MinioConfig;
import top.aiolife.core.util.MinioUtil;
import top.aiolife.record.pojo.entity.FileEntity;
import top.aiolife.record.service.FilePreviewGuard;
import top.aiolife.record.service.IFileService;
import top.aiolife.sso.api.FileController;
import top.aiolife.sso.service.SecondaryLockGuard;
import top.aiolife.system.mapper.StorageObjectMapper;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static top.aiolife.support.ImageFixtures.image;

class FilePreviewSecurityTest {
    private final MinioUtil minio = mock(MinioUtil.class);
    private final IFileService files = mock(IFileService.class);
    private final FilePreviewGuard guard = mock(FilePreviewGuard.class);
    private final FileEntity file = new FileEntity();
    private MockMvc mvc;
    private static final String ID = "a".repeat(32);
    private static final String OBJECT = "1/avatar/fake.png";

    @BeforeEach
    void setup() {
        var config = new MinioConfig();
        config.setBucketName("aiolife");
        mvc = MockMvcBuilders.standaloneSetup(new SysFileController(mock(StorageObjectMapper.class), files, minio, config, guard, mock(SecondaryLockGuard.class)),
                new FileController(minio, config, new CbtiConfig(), files, guard)).build();
        file.setId(ID);
        file.setFileName(OBJECT);
        file.setFileType("text/html");
        file.setIsPublic(1);
        when(files.getById(ID)).thenReturn(file);
        when(guard.resolveLoginUserId()).thenReturn(null);
        when(guard.check(file, null)).thenReturn(FilePreviewGuard.AccessDecision.ALLOW);
        when(minio.objectExists("aiolife", OBJECT)).thenReturn(true);
    }

    private String url(boolean legacy) { return "/file/preview/" + (legacy ? "aiolife/" + OBJECT : ID); }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 两种入口均把历史HTML和伪装图片作为附件返回(boolean legacy) throws Exception {
        byte[] html = "<!doctype html><script>alert(document.domain)</script>".getBytes(StandardCharsets.UTF_8);
        when(minio.getFile("aiolife", OBJECT)).thenAnswer(call -> new ByteArrayInputStream(html));
        // 无论数据库 MIME 声称 HTML 还是图片，都不能绕过内容验证。
        for (String declared : new String[]{"text/html", "image/png", "image/svg+xml", null}) {
            file.setFileType(declared);
            mvc.perform(get(url(legacy))).andExpect(status().isOk())
                    .andExpect(content().contentType("application/octet-stream"))
                    .andExpect(header().string("Content-Disposition", startsWith("attachment;")))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                    .andExpect(header().string("Content-Security-Policy", "default-src 'none'; sandbox"))
                    .andExpect(content().bytes(html));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 两种入口均按真实内容正常预览历史图片(boolean legacy) throws Exception {
        byte[] png = image("png");
        when(minio.getFile("aiolife", OBJECT)).thenReturn(new ByteArrayInputStream(png));
        mvc.perform(get(url(legacy))).andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(header().string("Content-Disposition", startsWith("inline;")))
                .andExpect(content().bytes(png));
    }

    @ParameterizedTest
    @ValueSource(strings = {"UNAUTHORIZED", "FORBIDDEN"})
    void 无权限不能读取对象或返回文件内容(String decision) throws Exception {
        when(guard.check(file, null)).thenReturn(FilePreviewGuard.AccessDecision.valueOf(decision));
        mvc.perform(get(url(false))).andExpect(status().is(decision.equals("UNAUTHORIZED") ? 401 : 403));
        verify(minio, never()).getFile(anyString(), anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"png", "jpeg"})
    void 显式下载图片仍然强制下载(String format) throws Exception {
        byte[] bytes = image(format);
        when(minio.getFile("aiolife", OBJECT)).thenReturn(new ByteArrayInputStream(bytes));
        mvc.perform(get("/file/download/" + ID)).andExpect(status().isOk())
                .andExpect(content().contentType("application/octet-stream"))
                .andExpect(header().string("Content-Disposition", startsWith("attachment;")))
                .andExpect(content().bytes(bytes));
    }
}
