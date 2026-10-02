package top.aiolife.system.service;

import io.minio.MinioAsyncClient;
import io.minio.messages.ListBucketResultV2;
import org.springframework.stereotype.Component;
import top.aiolife.config.MinioConfig;

/** 保留 S3 原生分页游标，避免 SDK Iterable 自动遍历整个桶。 */
@Component
public class StorageListClient extends MinioAsyncClient {
    public StorageListClient(MinioConfig config) {
        super(MinioAsyncClient.builder().endpoint(config.getEndpoint())
                .credentials(config.getAccessKey(), config.getSecretKey()).build());
    }

    public ListBucketResultV2 page(String bucket, String prefix, String cursor, int size) throws Exception {
        return listObjectsV2(bucket, null, "/", "url", null, size, prefix,
                cursor, false, false, null, null).result();
    }
}
