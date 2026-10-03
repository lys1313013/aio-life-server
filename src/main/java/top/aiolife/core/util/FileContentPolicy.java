package top.aiolife.core.util;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ContentDisposition;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** 上传和历史文件预览共用的内容策略，不信任文件后缀、数据库或客户端 MIME。 */
public final class FileContentPolicy {
    public static final int MAX_IMAGE_BYTES = 10 * 1024 * 1024;
    private static final long MAX_IMAGE_PIXELS = 16_000_000;

    private FileContentPolicy() {}

    public record ImageType(String contentType, String extension) {}

    /** 先限制尺寸再解码首帧，保留 GIF/WebP 原始动画；只允许栅格格式。 */
    public static ImageType detectImage(byte[] bytes) {
        if (bytes.length == 0 || bytes.length > MAX_IMAGE_BYTES) return null;
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) return null;
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                ImageType type = switch (reader.getFormatName().toLowerCase(Locale.ROOT)) {
                    case "jpeg", "jpg" -> new ImageType("image/jpeg", "jpg");
                    case "png" -> new ImageType("image/png", "png");
                    case "gif" -> new ImageType("image/gif", "gif");
                    case "bmp" -> new ImageType("image/bmp", "bmp");
                    case "webp" -> new ImageType("image/webp", "webp");
                    default -> null;
                };
                if (type == null) return null;
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > MAX_IMAGE_PIXELS) return null;
                var image = reader.read(0);
                if (image == null) return null;
                image.flush();
                return type;
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    public static ImageType requireImage(byte[] bytes) {
        ImageType type = detectImage(bytes);
        if (type == null) {
            throw new IllegalArgumentException("仅支持不超过10MB、1600万像素的PNG、JPEG、GIF、BMP或WebP图片");
        }
        return type;
    }

    /** 历史对象按实际内容判定，未验证或超出预览上限的文件只下载，且完整流式返回。 */
    public static void writeResponse(InputStream input, HttpServletResponse response,
                                     String filename, boolean download) throws IOException {
        byte[] prefix = download ? new byte[0] : input.readNBytes(MAX_IMAGE_BYTES + 1);
        ImageType image = download ? null : detectImage(prefix);
        boolean attachment = download || image == null;
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Content-Security-Policy", "default-src 'none'; sandbox");
        response.setContentType(attachment ? "application/octet-stream" : image.contentType());
        String name = filename == null ? "file" : filename.substring(filename.lastIndexOf('/') + 1);
        name = name.replaceAll("[\\p{Cntrl}]", "_");
        response.setHeader("Content-Disposition", (attachment ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(name.isBlank() ? "file" : name, StandardCharsets.UTF_8).build().toString());
        var output = response.getOutputStream();
        output.write(prefix);
        input.transferTo(output);
        output.flush();
    }
}
