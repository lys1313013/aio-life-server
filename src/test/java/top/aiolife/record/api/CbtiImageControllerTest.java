package top.aiolife.record.api;

import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.mock.web.MockMultipartFile;
import top.aiolife.core.lock.StorageObjectLock;
import top.aiolife.record.config.CbtiImageInitUtil;
import top.aiolife.record.mapper.ICbtiResultMapper;
import top.aiolife.record.pojo.entity.CbtiPersonalityEntity;
import top.aiolife.record.service.ICbtiService;
import top.aiolife.record.service.impl.CbtiServiceImpl;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    @Test void CBTI上传拒绝伪装图片且不写对象() {
        var minio = mock(MinioUtil.class);
        var personalities = mock(ICbtiPersonalityMapper.class);
        var personality = new CbtiPersonalityEntity();
        personality.setId(1L); personality.setCode("TEST");
        when(personalities.selectOne(any())).thenReturn(personality);
        var service = new CbtiServiceImpl(personalities,
                mock(ICbtiResultMapper.class), new ObjectMapper(),
                minio, new MinioConfig(), new CbtiConfig(), mock(CbtiImageInitUtil.class),
                mock(StorageObjectLock.class));
        assertThrows(IllegalArgumentException.class, () -> service.uploadPersonalityImage(
                1L,"TEST",new MockMultipartFile("file","fake.png","image/png","<svg/>".getBytes())));
        verifyNoInteractions(minio);
    }
    @Test void 仅当前人格绑定的图片可匿名读取且不能切换桶() throws Exception {
        var minio = mock(MinioUtil.class);
        var cbtiService = mock(ICbtiService.class);
        var config = new CbtiConfig(); config.setBucketName("cbti");
        var mvc = MockMvcBuilders.standaloneSetup(new CbtiImageController(minio, new MinioConfig(), config, cbtiService)).build();
        String key = "images/cbti/characters/public.png";
        mvc.perform(get("/cbti/images/" + key)).andExpect(status().isNotFound());
        verifyNoInteractions(minio);
        when(cbtiService.isBoundImageObject(key)).thenReturn(true);
        byte[] image = ImageFixtures.image("png");
        when(minio.getFile("cbti", key)).thenReturn(new ByteArrayInputStream(image));
        when(minio.getContentType("cbti", key)).thenReturn("image/png");
        mvc.perform(get("/cbti/images/" + key)).andExpect(status().isOk())
                .andExpect(content().contentType("image/png")).andExpect(content().bytes(image));
        mvc.perform(get("/file/preview/aiolife/1/avatar/private.png")).andExpect(status().isNotFound());
    }
}
