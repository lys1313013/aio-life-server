package top.aiolife.record.autotask;

import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import java.time.Duration;
import java.time.Instant;
import top.aiolife.config.MinioConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import top.aiolife.record.service.AttachmentCollectionService;
import top.aiolife.system.mapper.StorageObjectMapper;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name="aio.life.scheduling.enabled",havingValue="true",matchIfMissing=true)
public class AttachmentCollectionTask {
    private final AttachmentCollectionService collection;
    private final StorageObjectMapper objects;
    private final MinioClient minio;
    private final MinioConfig config;
    @Scheduled(fixedDelayString="${aio.life.server.attachment.collect-ms:3600000}",initialDelay=60000)
    public void execute() {
        collection.expire();
        for(var object:objects.collectionCandidates()) try { collection.collect(object.getId()); }
        catch(Exception e) { log.warn("附件对象回收暂未完成 objectId={},cause={}",object.getId(),e.getClass().getSimpleName()); }
        // 覆盖上传字节成功但数据库事务回滚产生的无索引对象，只扫描内容寻址前缀。
        try {
            String bucket=config.getBucketName();
            var cutoff=Instant.now().minus(Duration.ofDays(7));
            for(var result:minio.listObjects(ListObjectsArgs.builder().bucket(bucket).prefix("objects/").recursive(true).build())) {
                var item=result.get();
                if(!item.isDir() && item.lastModified().toInstant().isBefore(cutoff)) collection.collectOrphan(bucket,item.objectName());
            }
        } catch(Exception e) { log.warn("孤立字节扫描暂未完成 cause={}",e.getClass().getSimpleName()); }
    }
}
