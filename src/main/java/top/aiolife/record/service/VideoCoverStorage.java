package top.aiolife.record.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import top.aiolife.core.lock.StorageObjectLock;
import top.aiolife.core.util.FileContentPolicy;
import top.aiolife.core.util.MinioUtil;
import top.aiolife.system.mapper.StorageObjectMapper;
import top.aiolife.system.pojo.entity.StorageObjectEntity;

@Service
@RequiredArgsConstructor
public class VideoCoverStorage {
    public static final String BUCKET = "system";
    public static final String PREFIX = "bvedio/";
    private final StorageObjectMapper objects;
    private final StorageObjectLock objectLock;
    private final MinioUtil minio;

    /** 跨用户以字节哈希去重；共享对象绝不能随某次业务回滚而删除。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public StorageObjectEntity store(byte[] bytes, long owner) {
        var type = FileContentPolicy.requireImage(bytes);
        String hash;
        try { hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
        String key = PREFIX + hash + "." + type.extension();
        objectLock.holdUntilTransactionCompletion(BUCKET, key);
        var object = objects.selectOne(new LambdaQueryWrapper<StorageObjectEntity>()
                .eq(StorageObjectEntity::getBucket, BUCKET).eq(StorageObjectEntity::getSha256, hash));
        if (object != null && object.getFileSize() != bytes.length)
            throw new IllegalStateException("图片哈希记录不一致");
        // 上传成功而数据库提交失败时可以留下孤立对象；重跑会安全地复用同一对象路径。
        if (!minio.objectExists(BUCKET, key)) {
            try { minio.putObject(BUCKET, key, new ByteArrayInputStream(bytes), bytes.length, type.contentType()); }
            catch (Exception e) { throw new IllegalStateException("IMAGE_STORAGE_FAILED", e); }
        }
        if (object == null) {
            object = new StorageObjectEntity();
            object.setBucket(BUCKET); object.setObjectKey(key); object.setSha256(hash);
            object.setFileSize((long) bytes.length); object.setContentType(type.contentType());
            object.fillCreateCommonField(owner);
            objects.insert(object);
        }
        return object;
    }
}
