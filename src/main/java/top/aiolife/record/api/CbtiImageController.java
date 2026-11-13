package top.aiolife.record.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import top.aiolife.config.CbtiConfig;
import top.aiolife.config.MinioConfig;
import top.aiolife.core.util.FileContentPolicy;
import top.aiolife.core.util.MinioUtil;
import top.aiolife.record.service.ICbtiService;

/** CBTI 公开人格图片只读取当前人格绑定的对象，不接受客户端指定存储桶。 */
@Tag(name = "CBTI 图片")
@RestController
@RequiredArgsConstructor
public class CbtiImageController {
    public static final String PATH = "/cbti/images/**";
    private final MinioUtil minio;
    private final MinioConfig minioConfig;
    private final CbtiConfig cbtiConfig;
    private final ICbtiService cbtiService;

    @Operation(summary = "读取 CBTI 人格图片")
    @GetMapping("/cbti/images/{*objectName}")
    public void image(@PathVariable String objectName, HttpServletResponse response) {
        String key = objectName.startsWith("/") ? objectName.substring(1) : objectName;
        if (!cbtiService.isBoundImageObject(key)) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String bucket = StringUtils.hasText(cbtiConfig.getBucketName()) ? cbtiConfig.getBucketName()
                : StringUtils.hasText(minioConfig.getBucketName()) ? minioConfig.getBucketName() : "aiolife";
        // 二进制响应已无法走统一 JSON 异常格式，读取失败直接返回 404。
        try (var input = minio.getFile(bucket, key)) {
            FileContentPolicy.writeResponse(input, response, key, false);
        } catch (Exception exception) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }
    }
}
