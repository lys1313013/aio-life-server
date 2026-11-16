package top.aiolife.record.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import top.aiolife.core.util.MinioUtil;
import top.aiolife.core.util.FileContentPolicy;
import top.aiolife.record.enums.FileBizType;
import top.aiolife.record.service.*;
import top.aiolife.system.mapper.StorageObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FileUploadContentSecurityTest {
    private final VideoCoverStorage storage=mock(VideoCoverStorage.class);
    private final FileServiceImpl service=new FileServiceImpl(mock(MinioUtil.class),storage,mock(StorageObjectMapper.class),mock(ExternalResourceService.class));
    @ParameterizedTest
    @EnumSource(value=FileBizType.class,names={"AVATAR","WARDROBE_ITEM","MOVIE","READ_RECORD","DEVICE","BANK_CARD_COVER"})
    void 图片业务拒绝伪装JPEG的HTML(FileBizType type) {
        assertThrows(IllegalArgumentException.class,()->service.upload(new MockMultipartFile("file","fake.jpg","image/jpeg","<script>alert(1)</script>".getBytes()),type));
        verifyNoInteractions(storage);
    }
    @ParameterizedTest
    @ValueSource(strings={"text/html","image/svg+xml","application/octet-stream"})
    void 头像拒绝SVG不信任声明类型(String declared) {
        assertThrows(IllegalArgumentException.class,()->service.upload(new MockMultipartFile("file","avatar.png",declared,"<svg/>".getBytes()),FileBizType.AVATAR));
        verifyNoInteractions(storage);
    }
    @Test void 超大上传在读取流和写存储前拒绝() throws Exception {
        var file=mock(MultipartFile.class);when(file.getSize()).thenReturn((long)FileContentPolicy.MAX_IMAGE_BYTES+1);
        assertThrows(IllegalArgumentException.class,()->service.upload(file,FileBizType.AVATAR));
        verify(file,never()).getInputStream();verifyNoInteractions(storage);
    }
}
