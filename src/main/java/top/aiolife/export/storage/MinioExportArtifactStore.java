package top.aiolife.export.storage;

import io.minio.BucketExistsArgs;
import io.minio.GetBucketPolicyArgs;
import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.UploadObjectArgs;
import io.minio.errors.ErrorResponseException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import top.aiolife.config.CbtiConfig;
import top.aiolife.config.MinioConfig;
import top.aiolife.export.config.ExportProperties;
import top.aiolife.export.service.ExportException;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** 独立预配置私有桶：不自动修改现有桶策略，不注册到通用 file 表。 */
@Component
@RequiredArgsConstructor
public class MinioExportArtifactStore implements ExportArtifactStore {
    private final MinioClient minio;
    private final MinioConfig common;
    private final CbtiConfig cbti;
    private final ExportProperties properties;

    public void verifyPrivateStorage() throws Exception {
        String bucket = properties.getBucket();
        if (bucket.equals(common.getBucketName())
                || bucket.equals(cbti.getBucketName())
                || !minio.bucketExists(BucketExistsArgs.builder().bucket(bucket).build()))
            throw ExportException.unavailable();
        try {
            String policy =
                    minio.getBucketPolicy(GetBucketPolicyArgs.builder().bucket(bucket).build());
            // 专用桶不接受任何 bucket policy，权限由服务凭据的 IAM 策略提供。
            if (policy != null && !policy.isBlank()) throw ExportException.unavailable();
        } catch (ErrorResponseException error) {
            if (!"NoSuchBucketPolicy".equals(error.errorResponse().code())) throw error;
        }
    }

    public void put(String key, Path file, String contentType) throws Exception {
        verifyPrivateStorage();
        minio.uploadObject(
                UploadObjectArgs.builder()
                        .bucket(properties.getBucket())
                        .object(key)
                        .filename(file.toString())
                        .contentType(contentType)
                        .build());
        long size =
                minio.statObject(
                                StatObjectArgs.builder()
                                        .bucket(properties.getBucket())
                                        .object(key)
                                        .build())
                        .size();
        if (size != Files.size(file))
            throw new IllegalStateException("Export artifact size mismatch");
    }

    public InputStream open(String key) throws Exception {
        verifyPrivateStorage();
        return minio.getObject(
                GetObjectArgs.builder().bucket(properties.getBucket()).object(key).build());
    }

    public List<StoredObject> scan(String after) throws Exception {
        verifyPrivateStorage();
        var request =
                ListObjectsArgs.builder()
                        .bucket(properties.getBucket())
                        .prefix("exports/")
                        .recursive(true);
        if (after != null) request.startAfter(after);
        var objects = new ArrayList<StoredObject>();
        for (var result : minio.listObjects(request.build())) {
            var item = result.get();
            objects.add(new StoredObject(item.objectName(), item.lastModified().toInstant()));
            if (objects.size() >= 100) break;
        }
        return objects;
    }

    public void delete(String key) throws Exception {
        minio.removeObject(
                RemoveObjectArgs.builder().bucket(properties.getBucket()).object(key).build());
    }
}
