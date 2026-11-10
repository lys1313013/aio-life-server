package top.aiolife.system.service;

import io.minio.MinioAsyncClient;
import io.minio.messages.ListBucketResultV2;
import java.util.concurrent.ExecutionException;
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
        try {
            return listObjectsV2Async(bucket, null, "/", "url", null, size, prefix,
                    cursor, false, false, null, null).get().result();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw exception;
        } catch (ExecutionException exception) {
            // 保留原同步接口的 S3 异常类型，调用方仍可区分权限、对象和网络错误。
            throwEncapsulatedException(exception);
            throw new IllegalStateException("读取对象列表失败", exception);
        }
    }
}
