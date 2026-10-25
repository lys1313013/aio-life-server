package top.aiolife.export.storage;

import java.io.InputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/** 存储实现不决定用户授权，只接受任务引擎生成的内部键。 */
public interface ExportArtifactStore {
    void verifyPrivateStorage() throws Exception;

    void put(String key, Path file, String contentType) throws Exception;

    InputStream open(String key) throws Exception;

    void delete(String key) throws Exception;

    List<StoredObject> scan(String after) throws Exception;

    record StoredObject(String key, Instant modifiedAt) {}
}
