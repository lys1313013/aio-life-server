package top.aiolife.record.autotask;

import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.Result;
import io.minio.messages.Item;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import top.aiolife.config.MinioConfig;
import top.aiolife.core.lock.DistributedLockExecutor;
import top.aiolife.core.redis.RedisUtil;
import top.aiolife.record.service.AttachmentCollectionService;
import top.aiolife.system.mapper.StorageObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class AttachmentCollectionTaskTest {
    @Test void 从游标恢复并批量跳过已登记对象() throws Exception {
        var collection = mock(AttachmentCollectionService.class);
        var locks = mock(DistributedLockExecutor.class);
        var redis = mock(RedisUtil.class);
        var objects = mock(StorageObjectMapper.class);
        var minio = mock(MinioClient.class);
        var config = new MinioConfig();
        config.setBucketName("fixture");

        when(locks.tryRun(eq("attachment:collection:fixture"), any())).thenAnswer(call -> {
            call.getArgument(1, Runnable.class).run();
            return true;
        });
        String cursorKey = "attachment:orphan-scan:cursor:fixture";
        when(redis.get(cursorKey)).thenReturn("objects/previous");
        var old = ZonedDateTime.now().minusDays(8);
        List<Result<Item>> results = new ArrayList<>();
        results.add(item("objects/registered", old));
        results.add(item("objects/orphan", old));
        for (int i = 2; i <= 1000; i++) results.add(item("objects/young-" + i, ZonedDateTime.now()));
        when(minio.listObjects(any())).thenReturn(results);
        when(objects.findStoredKeys("fixture", List.of("objects/registered", "objects/orphan")))
                .thenReturn(List.of("objects/registered"));

        new AttachmentCollectionTask(collection, locks, redis, objects, minio, config).execute();

        var request = ArgumentCaptor.forClass(ListObjectsArgs.class);
        verify(minio).listObjects(request.capture());
        assertEquals("objects/previous", request.getValue().startAfter());
        assertEquals(1000, request.getValue().maxKeys());
        verify(collection).collectOrphan("fixture", "objects/orphan");
        verify(collection, never()).collectOrphan("fixture", "objects/registered");
        verify(redis).set(cursorKey, "objects/young-999");
    }

    private Result<Item> item(String key, ZonedDateTime modifiedAt) {
        var item = mock(Item.class);
        when(item.objectName()).thenReturn(key);
        when(item.lastModified()).thenReturn(modifiedAt);
        return new Result<>(item);
    }
}
