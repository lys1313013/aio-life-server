package top.aiolife.record.autotask;

import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import top.aiolife.core.lock.DistributedLockExecutor;
import top.aiolife.core.redis.RedisUtil;
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
    private static final int SCAN_BATCH_SIZE = 1000;
    private static final String SCAN_CURSOR = "attachment:orphan-scan:cursor:";
    private final AttachmentCollectionService collection;
    private final DistributedLockExecutor locks;
    private final RedisUtil redis;
    private final StorageObjectMapper objects;
    private final MinioClient minio;
    private final MinioConfig config;
    @Scheduled(fixedDelayString="${aio.life.server.attachment.collect-ms:3600000}",initialDelay=60000)
    public void execute() {
        String bucket = config.getBucketName();
        locks.tryRun("attachment:collection:" + bucket, () -> {
            collection.expire();
            for (var object : objects.collectionCandidates()) {
                try {
                    collection.collect(object.getId());
                } catch (Exception e) {
                    log.warn("附件对象回收暂未完成 objectId={},cause={}", object.getId(), e.getClass().getSimpleName());
                }
            }
            scanOrphans(bucket);
        });
    }

    private void scanOrphans(String bucket) {
        String cursorKey = SCAN_CURSOR + bucket;
        String cursor = redis.get(cursorKey);
        var request = ListObjectsArgs.builder().bucket(bucket).prefix("objects/").recursive(true)
                .maxKeys(SCAN_BATCH_SIZE);
        if (cursor != null && !cursor.isBlank()) request.startAfter(cursor);

        var cutoff = Instant.now().minus(Duration.ofDays(7));
        List<String> candidates = new ArrayList<>();
        try {
            var results = minio.listObjects(request.build()).iterator();
            int scanned = 0;
            while (scanned < SCAN_BATCH_SIZE && results.hasNext()) {
                var item = results.next().get();
                cursor = item.objectName();
                scanned++;
                if (!item.isDir() && item.lastModified().toInstant().isBefore(cutoff)) candidates.add(cursor);
            }
            if (!candidates.isEmpty()) {
                var registered = new HashSet<>(objects.findStoredKeys(bucket, candidates));
                for (String key : candidates) {
                    if (registered.contains(key)) continue;
                    try {
                        collection.collectOrphan(bucket, key);
                    } catch (Exception e) {
                        log.warn("孤立附件回收暂未完成，留待下一轮复查 cause={}", e.getClass().getSimpleName());
                    }
                }
            }
            // 满批时保留进度。到达末尾后再从头开始，复查新出现和暂时失败的对象。
            if (scanned == SCAN_BATCH_SIZE) redis.set(cursorKey, cursor);
            else redis.delete(cursorKey);
        } catch (Exception e) {
            log.warn("孤立字节扫描暂未完成 cause={}", e.getClass().getSimpleName());
        }
    }
}
