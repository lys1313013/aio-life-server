package top.aiolife.sso.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import top.aiolife.config.CbtiConfig;
import top.aiolife.config.MinioConfig;
import top.aiolife.core.util.MinioUtil;
import top.aiolife.record.service.FilePreviewGuard;
import top.aiolife.record.service.IFileService;

import java.io.ByteArrayInputStream;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FileControllerTest {
    private static final String AVATAR = "13/avatar/bd835abf-8b67-47a0-89ab-99e7fa8fa038.png";
    private final MinioUtil minioUtil = mock(MinioUtil.class);
    private final IFileService fileService = mock(IFileService.class);
    private final FilePreviewGuard guard = mock(FilePreviewGuard.class);
    private final MinioConfig minioConfig = new MinioConfig();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        minioConfig.setBucketName("aiolife");
        CbtiConfig cbtiConfig = new CbtiConfig();
        cbtiConfig.setBucketName("cbti");
        mvc = MockMvcBuilders.standaloneSetup(
                new FileController(minioUtil, minioConfig, cbtiConfig, fileService, guard)).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"aiolife", "custom-bucket", ""})
    void preview_默认桶头像无需登录或文件记录(String configuredBucket) throws Exception {
        minioConfig.setBucketName(configuredBucket);
        String bucket = configuredBucket.isEmpty() ? "aiolife" : configuredBucket;
        byte[] content = top.aiolife.support.ImageFixtures.image("png");
        when(minioUtil.getFile(bucket, AVATAR)).thenReturn(new ByteArrayInputStream(content));

        mvc.perform(get("/file/preview/" + bucket + "/" + AVATAR))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(content));

        verifyNoInteractions(fileService, guard);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "aiolife/13/bank-card/private.png",
            "aiolife/13/wardrobe/private.png",
            "aiolife/13/avatar/nested/private.png",
            "cbti/" + AVATAR
    })
    void preview_私有路径仍要求登录(String path) throws Exception {
        when(guard.resolveLoginUserId()).thenReturn(null);
        mvc.perform(get("/file/preview/" + path)).andExpect(status().isUnauthorized());
        verifyNoInteractions(minioUtil, fileService);
    }

    @Test
    void preview_其他桶不能通过头像路径访问() throws Exception {
        mvc.perform(get("/file/preview/other/" + AVATAR)).andExpect(status().isNotFound());
        verifyNoInteractions(minioUtil, fileService, guard);
    }

    @Test
    void preview_CBTI公共图片仍可匿名访问() throws Exception {
        String object = "images/cbti/characters/public.png";
        when(minioUtil.getFile("cbti", object)).thenReturn(new ByteArrayInputStream(top.aiolife.support.ImageFixtures.image("png")));
        mvc.perform(get("/file/preview/cbti/" + object)).andExpect(status().isOk());
        verifyNoInteractions(fileService, guard);
    }
}
