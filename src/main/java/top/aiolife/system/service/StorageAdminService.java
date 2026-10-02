package top.aiolife.system.service;

import top.aiolife.core.lock.StorageObjectLock;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import top.aiolife.config.MinioConfig;
import top.aiolife.system.pojo.query.StorageObjectQuery;
import top.aiolife.system.pojo.vo.StorageObjectVO;
import top.aiolife.system.pojo.vo.StoragePageVO;

/** 管理员浏览与清理配置的业务桶，不接受调用方指定其它桶或存储地址。 */
@Service
@RequiredArgsConstructor
public class StorageAdminService {
    private static final long MAX_PREVIEW_SIZE = 20 * 1024 * 1024;
    private static final Map<String, String> IMAGE_TYPES = Map.of(
            "jpg", "image/jpeg", "jpeg", "image/jpeg", "png", "image/png",
            "gif", "image/gif", "webp", "image/webp", "avif", "image/avif", "bmp", "image/bmp");
    private final MinioConfig config;
    private final StorageListClient listClient;
    private final MinioClient minioClient;
    private final StorageFileReferenceGuard referenceGuard;
    private final StorageObjectLock objectLock;

    public StoragePageVO list(StorageObjectQuery query) throws Exception {
        String bucket = bucket();
        var result = listClient.page(bucket, query.getPrefix(), query.getCursor(), query.getPageSize());
        var items = new ArrayList<StorageObjectVO>();
        for (var directory : result.commonPrefixes()) {
            var item = directory.toItem();
            item.setEncodingType(result.encodingType());
            items.add(new StorageObjectVO(item.objectName(), true, 0, null, false));
        }
        for (var item : result.contents()) {
            item.setEncodingType(result.encodingType());
            items.add(new StorageObjectVO(item.objectName(), false, item.size(),
                    item.lastModified() == null ? null : item.lastModified().toInstant().toString(),
                    imageType(item.objectName()) != null && item.size() <= MAX_PREVIEW_SIZE));
        }
        return new StoragePageVO(bucket, query.getPrefix(), items,
                result.isTruncated() ? result.nextContinuationToken() : null);
    }

    public void read(String key, boolean download, HttpServletResponse response) throws Exception {
        validateKey(key);
        String bucket = bucket();
        var stat = minioClient.statObject(StatObjectArgs.builder().bucket(bucket).object(key).build());
        String type = imageType(key);
        if (!download && (type == null || stat.size() > MAX_PREVIEW_SIZE)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "此文件不支持预览，请下载查看（图片预览上限 20 MB）");
        }
        // 仅内联栅格图片；HTML/SVG 等一律通过 attachment 下载，且不生成可绕过角色校验的签名 URL。
        try (var input = minioClient.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build())) {
            response.setHeader("Cache-Control", "no-store");
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Content-Security-Policy", "default-src 'none'; sandbox");
            response.setContentType(download ? "application/octet-stream" : type);
            response.setContentLengthLong(stat.size());
            String name = key.substring(key.lastIndexOf('/') + 1);
            response.setHeader("Content-Disposition", (download ? ContentDisposition.attachment() : ContentDisposition.inline())
                    .filename(name.isEmpty() ? "file" : name, StandardCharsets.UTF_8).build().toString());
            input.transferTo(response.getOutputStream());
        }
    }

    /** 每次实际删除都重新查库，不依赖前端预检结果；查询失败时不执行 MinIO 删除。 */
    public void delete(String key) throws Exception {
        validateKey(key);
        if (key.endsWith("/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持删除目录，请选择具体文件");
        }
        String bucket = bucket();
        try (var lease = objectLock.acquire(bucket, key)) {
            referenceGuard.check(bucket, key);
            minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
        }
    }

    private void validateKey(String key) {
        if (!StringUtils.hasText(key) || key.getBytes(StandardCharsets.UTF_8).length > 1024) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "对象名无效");
        }
    }

    private String bucket() {
        if (!StringUtils.hasText(config.getBucketName())) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "尚未配置 MinIO 业务桶");
        }
        return config.getBucketName();
    }

    private static String imageType(String key) {
        return IMAGE_TYPES.get(key.substring(key.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT));
    }
}
