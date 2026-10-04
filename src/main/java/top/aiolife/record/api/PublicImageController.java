package top.aiolife.record.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import top.aiolife.config.MinioConfig;
import top.aiolife.core.util.FileContentPolicy;
import top.aiolife.core.util.MinioUtil;
import top.aiolife.record.mapper.PublicImageMapper;

import java.io.IOException;
import java.util.Objects;

/** 匿名公共底图入口。文件 ID 随换图变化，私人文件继续走鉴权预览。 */
@RestController
@RequiredArgsConstructor
public class PublicImageController {
    public static final String PATH = "/public/images/*";
    private final PublicImageMapper images;
    private final MinioUtil minio;
    private final MinioConfig config;

    @GetMapping("/public/images/{id:[a-fA-F0-9]{32}}.{extension:png|jpg|webp|gif|bmp}")
    @Operation(summary = "读取已发布的公共图片（无需登录）")
    @ApiResponse(responseCode = "200", description = "图片二进制内容，不是 ApiResponse JSON",
            content = @Content(mediaType = "image/*",
                    schema = @Schema(type = "string", format = "binary")))
    public void image(@PathVariable String id, @PathVariable String extension,
                      HttpServletRequest request, HttpServletResponse response) throws IOException {
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
        if (!"HEAD".equals(request.getMethod())) response.getOutputStream().write(bytes);
    }
}
