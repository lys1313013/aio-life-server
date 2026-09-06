package top.aiolife.record.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import top.aiolife.record.pojo.req.MovieReq;
import top.aiolife.record.pojo.vo.FileVO;
import top.aiolife.record.service.IFileService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MovieServiceImplRexxarTest {

    private final IFileService fileService = mock(IFileService.class);
    private final MovieServiceImpl movieService = new MovieServiceImpl(fileService);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void applyRexxarData_解析当前CoverImage结构() throws Exception {
        JsonNode root = objectMapper.readTree("""
                {
                  "title": "问苍茫",
                  "cover": {
                    "image": {
                      "large": {"url": "https://img1.doubanio.com/view/photo/l/public/p2901874168.jpg"}
                    }
                  },
                  "cover_url": "https://img1.doubanio.com/view/photo/m_ratio_poster/public/p2901874168.jpg",
                  "directors": [{"name": "王伟"}],
                  "episodes_count": 32
                }
                """);
        MovieReq result = new MovieReq();
        result.setType(1);

        movieService.applyRexxarData(root, result);

        assertEquals("问苍茫", result.getTitle());
        assertEquals("https://img1.doubanio.com/view/photo/l/public/p2901874168.jpg", result.getCoverImgUrl());
        assertEquals("王伟", result.getDirector());
        assertEquals(32, result.getTotalProgress());
        assertEquals(2, result.getType());
    }

    @Test
    void applyRexxarData_兼容旧Pic结构() throws Exception {
        JsonNode root = objectMapper.readTree("""
                {
                  "title": "测试电影",
                  "pic": {"large": "https://example.com/cover.jpg"},
                  "durations": ["126分钟"]
                }
                """);
        MovieReq result = new MovieReq();
        result.setType(1);

        movieService.applyRexxarData(root, result);

        assertEquals("https://example.com/cover.jpg", result.getCoverImgUrl());
        assertEquals(126, result.getTotalProgress());
        assertEquals(1, result.getType());
    }

    @Test
    void ensureCoverUploaded_上传成功后回填FileId() {
        MovieReq result = new MovieReq();
        result.setCoverImgUrl("https://img1.doubanio.com/cover.jpg");
        FileVO file = new FileVO();
        file.setId("cover-file-id");
        when(fileService.uploadFromUrl(anyString(), any())).thenReturn(file);

        movieService.ensureCoverUploaded(result, true);

        assertEquals("cover-file-id", result.getFileId());
        verify(fileService).uploadFromUrl(anyString(), any());
    }

    @Test
    void ensureCoverUploaded_保存阶段上传失败时不再静默忽略() {
        MovieReq result = new MovieReq();
        result.setCoverImgUrl("https://img1.doubanio.com/cover.jpg");
        when(fileService.uploadFromUrl(anyString(), any()))
                .thenThrow(new IllegalStateException("MinIO unavailable"));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> movieService.ensureCoverUploaded(result, true));

        assertEquals("封面图上传失败，请确认 MinIO 服务可用后重试", exception.getMessage());
    }
}
