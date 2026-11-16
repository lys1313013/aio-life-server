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

/** 写入时校验真实内容，读取时使用服务端已确认的类型流式返回。 */
public final class FileContentPolicy {
    public static final int MAX_IMAGE_BYTES = 10 * 1024 * 1024;
    private static final long MAX_IMAGE_PIXELS = 16_000_000;

    private FileContentPolicy() {}

    public record ImageType(String contentType, String extension, int width, int height) {}

    /** 只允许已校验的栅格类型内联；未知类型及活动内容始终下载。 */
    public static boolean isInlineImage(String contentType) {
        return contentType != null && switch (contentType) {
            case "image/jpeg", "image/png", "image/gif", "image/bmp", "image/webp" -> true;
            default -> false;
        };
    }

    /** 先限制尺寸再解码首帧，保留 GIF/WebP 原始动画；只允许栅格格式。 */
    public static ImageType detectImage(byte[] bytes) {
        if (bytes.length == 0 || bytes.length > MAX_IMAGE_BYTES) return null;
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) return null;
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String extension = switch (reader.getFormatName().toLowerCase(Locale.ROOT)) {
                    case "jpeg", "jpg" -> "jpg";
                    case "png", "gif", "bmp", "webp" -> reader.getFormatName().toLowerCase(Locale.ROOT);
                    default -> null;
                };
                if (extension == null) return null;
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > MAX_IMAGE_PIXELS) return null;
                var image = reader.read(0);
                if (image == null) return null;
                image.flush();
                return new ImageType("image/" + (extension.equals("jpg") ? "jpeg" : extension), extension, width, height);
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

    /** contentType 只能来自写入时的服务端校验结果，不能传客户端 MIME 或文件后缀。 */
    public static void writeResponse(InputStream input, HttpServletResponse response,
                                     String filename, boolean download, String contentType) throws IOException {
        boolean attachment = download || !isInlineImage(contentType);
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Content-Security-Policy", "default-src 'none'; sandbox");
        response.setContentType(attachment ? "application/octet-stream" : contentType);
        String name = filename == null ? "file" : filename.substring(filename.lastIndexOf('/') + 1);
        name = name.replaceAll("[\\p{Cntrl}]", "_");
        response.setHeader("Content-Disposition", (attachment ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(name.isBlank() ? "file" : name, StandardCharsets.UTF_8).build().toString());
        var output = response.getOutputStream();
        input.transferTo(output);
        output.flush();
    }
}
