package top.aiolife.record.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;
import top.aiolife.config.MinioConfig;
import top.aiolife.core.util.FileContentPolicy;
import top.aiolife.core.util.MinioUtil;
import top.aiolife.record.mapper.PublicImageMapper;

/**
 * 匿名公共底图读取服务。
 *
 * @author Lys
 */
@Service
@RequiredArgsConstructor
public class PublicImageService {

    private final PublicImageMapper images;
    private final MinioUtil minio;
    private final MinioConfig config;

    /**
     * 输出已发布图片，按内容检测类型并支持 ETag 协商缓存。
     */
    public void write(String id, String extension, HttpServletRequest request,
                      HttpServletResponse response) throws IOException {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Content-Security-Policy", "default-src 'none'; sandbox");
        var file = images.selectPublished(id);
        if (file == null || !StringUtils.hasText(file.getFileName())) {
            response.setStatus(404);
            return;
        }
        String bucket = StringUtils.hasText(config.getBucketName()) ? config.getBucketName() : "aiolife";
        byte[] bytes;
        try (var input = minio.getFile(bucket, file.getFileName())) {
            bytes = input.readNBytes(FileContentPolicy.MAX_IMAGE_BYTES + 1);
        } catch (Exception e) {
            response.setStatus(404);
            return;
        }
        var type = FileContentPolicy.detectImage(bytes);
        if (type == null || !Objects.equals(extension, type.extension())) {
            response.setStatus(404);
            return;
        }
        // 按实际内容生成 ETag，避免仅依赖声明的 MIME/后缀或历史哈希字段。
        String etag = "\"" + DigestUtils.md5DigestAsHex(bytes) + "\"";
        response.setContentType(type.contentType());
        response.setHeader("ETag", etag);
        response.setHeader("Cache-Control", "public, max-age=86400, s-maxage=2592000");
        if (etag.equals(request.getHeader("If-None-Match"))) {
            response.setStatus(304);
            return;
        }
        response.setContentLength(bytes.length);
        if (!"HEAD".equals(request.getMethod())) {
            response.getOutputStream().write(bytes);
        }
    }
}
