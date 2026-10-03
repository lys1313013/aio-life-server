package top.aiolife.core.util;

import io.minio.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import top.aiolife.config.MinioConfig;

import java.io.InputStream;
import java.util.UUID;

/**
 * MinIO 工具类
 */
@Slf4j
@Component
public class MinioUtil {

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private MinioConfig minioConfig;

    @Value("${aio.life.server.base-url}")
    private String serveBaseUrl;

    /** 公开头像按文件 ID 预览，客户端不再绑定对象路径或历史域名。 */
    public String getFilePreviewUrl(String fileId) {
        if (fileId == null || !fileId.matches("[a-fA-F0-9]{32}"))
            throw new IllegalArgumentException("文件 ID 无效");
        return serveBaseUrl.replaceAll("/+$", "") + "/file/preview/" + fileId;
    }

    /**
     * 获取文件的预览 URL
     *
     * @param bucketName 桶名
     * @param objectName 对象名
     * @return 完整的预览 URL
     */
    public String getPreviewUrl(String bucketName, String objectName) {
        String normalized = objectName.startsWith("/") ? objectName.substring(1) : objectName;
        return serveBaseUrl + "/file/preview/" + bucketName + "/" + normalized;
    }

    /**
     * 上传文件到 MinIO
     *
     * @param bucketName 桶名
     * @param file       上传的文件
     * @param objectName 文件对象名
     * @return 文件访问 URL
     */
    public String uploadFile(String bucketName, MultipartFile file, String objectName) throws Exception {
        ensureBucketExists(bucketName);

        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );

            return objectName;
        }
    }

    public void putObject(String bucketName, String objectName, InputStream inputStream, long size, String contentType) throws Exception {
        ensureBucketExists(bucketName);
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .stream(inputStream, size, -1)
                        .contentType(contentType)
                        .build()
        );
    }

    public boolean objectExists(String bucketName, String objectName) {
        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取文件流
     * @param bucketName 桶名
     * @param objectName 文件对象名
     * @return 文件输入流
     */
    public InputStream getFile(String bucketName, String objectName) throws Exception {
        return minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .build()
        );
    }

    /**
     * 生成唯一的文件名
     * @param originalFilename 原始文件名
     * @return 唯一文件名
     */
    public String generateUniqueFileName(String originalFilename) {
        String extension = originalFilename.substring(originalFilename.lastIndexOf('.'));
        return UUID.randomUUID().toString() + extension;
    }

    /**
     * 删除文件
     * @param bucketName 桶名
     * @param objectName 文件对象名
     */
    public void removeObject(String bucketName, String objectName) throws Exception {
        minioClient.removeObject(
                RemoveObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .build()
        );
    }

    /**
     * 复制文件
     * @param sourceBucket 源桶名
     * @param sourceObject 源对象名
     * @param targetBucket 目标桶名
     * @param targetObject 目标对象名
     */
    public void copyObject(String sourceBucket, String sourceObject, String targetBucket, String targetObject) throws Exception {
        ensureBucketExists(targetBucket);
        minioClient.copyObject(
                CopyObjectArgs.builder()
                        .source(CopySource.builder().bucket(sourceBucket).object(sourceObject).build())
                        .bucket(targetBucket)
                        .object(targetObject)
                        .build()
        );
    }

    /**
     * 确保桶存在，如果不存在则创建
     * @param bucketName 桶名
     */
    private void ensureBucketExists(String bucketName) throws Exception {
        boolean exists = minioClient.bucketExists(
                BucketExistsArgs.builder()
                        .bucket(bucketName)
                        .build()
        );

        if (!exists) {
            minioClient.makeBucket(
                    MakeBucketArgs.builder()
                            .bucket(bucketName)
                            .build()
            );
            log.info("创建 MinIO 桶: {}", bucketName);
        }
    }
}
