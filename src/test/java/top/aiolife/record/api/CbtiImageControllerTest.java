package top.aiolife.record.api;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import top.aiolife.config.CbtiConfig;
import top.aiolife.config.MinioConfig;
import top.aiolife.core.util.MinioUtil;
import top.aiolife.record.mapper.ICbtiPersonalityMapper;
import top.aiolife.support.ImageFixtures;
import java.io.ByteArrayInputStream;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CbtiImageControllerTest {
    @Test void 仅当前人格绑定的图片可匿名读取且不能切换桶() throws Exception {
        var minio = mock(MinioUtil.class);
        var personalities = mock(ICbtiPersonalityMapper.class);
        var config = new CbtiConfig(); config.setBucketName("cbti");
        var mvc = MockMvcBuilders.standaloneSetup(new CbtiImageController(minio, new MinioConfig(), config, personalities)).build();
        String key = "images/cbti/characters/public.png";
        mvc.perform(get("/cbti/images/" + key)).andExpect(status().isNotFound());
        verifyNoInteractions(minio);
        when(personalities.selectCount(any())).thenReturn(1L);
        byte[] image = ImageFixtures.image("png");
        when(minio.getFile("cbti", key)).thenReturn(new ByteArrayInputStream(image));
        mvc.perform(get("/cbti/images/" + key)).andExpect(status().isOk())
                .andExpect(content().contentType("image/png")).andExpect(content().bytes(image));
        mvc.perform(get("/file/preview/aiolife/1/avatar/private.png")).andExpect(status().isNotFound());
    }
}
