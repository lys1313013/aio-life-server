package top.aiolife.record.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.aiolife.config.MinioConfig;
import top.aiolife.core.lock.StorageObjectLock;
import top.aiolife.core.util.FileContentPolicy;
import top.aiolife.core.util.MinioUtil;
import top.aiolife.system.mapper.StorageObjectMapper;
import top.aiolife.system.pojo.entity.StorageObjectEntity;

/** 上传、远程导入与回收使用同一内容锁，锁持有至事务完成。 */
@Service
@RequiredArgsConstructor
public class VideoCoverStorage {
    private final StorageObjectMapper objects;
    private final StorageObjectLock objectLock;
    private final MinioUtil minio;
    private final MinioConfig config;

    @Transactional(propagation = Propagation.MANDATORY)
    public StorageObjectEntity store(byte[] bytes, Long owner) {
        if (bytes.length == 0 || bytes.length > FileContentPolicy.MAX_IMAGE_BYTES)
            throw new IllegalArgumentException("文件必须为1字节至10MB");
        var type = FileContentPolicy.detectImage(bytes);
        String hash;
        try { hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
        String bucket = StringUtils.hasText(config.getBucketName()) ? config.getBucketName() : "aiolife";
        String key = "objects/" + hash.substring(0, 2) + "/" + hash + "." + (type == null ? "bin" : type.extension());
        objectLock.holdUntilTransactionCompletion(bucket, key);
        var object = objects.selectOne(new LambdaQueryWrapper<StorageObjectEntity>()
                .eq(StorageObjectEntity::getBucket, bucket).eq(StorageObjectEntity::getContentHash, hash));
        if (object != null && object.getFileSize() != bytes.length)
            throw new IllegalStateException("文件摘要记录不一致");
        // 先写入字节再发布数据库引用。回滚只留下可安全清扫的孤立字节，不删除共享内容。
        if (!minio.objectExists(bucket, key)) {
            try { minio.putObject(bucket, key, new ByteArrayInputStream(bytes), bytes.length,
                    type == null ? "application/octet-stream" : type.contentType()); }
            catch (Exception e) { throw new IllegalStateException("IMAGE_STORAGE_FAILED", e); }
        }
        if (object == null) {
            object = new StorageObjectEntity();
            object.setBucket(bucket); object.setObjectKey(key); object.setContentHash(hash);
            object.setFileSize((long) bytes.length);
            object.setContentType(type == null ? "application/octet-stream" : type.contentType());
            if (type != null) try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
                var reader = ImageIO.getImageReaders(input).next();
                try { reader.setInput(input, true, true); object.setImageWidth(reader.getWidth(0)); object.setImageHeight(reader.getHeight(0)); }
                finally { reader.dispose(); }
            } catch (Exception e) { throw new IllegalArgumentException("无法读取图片尺寸", e); }
            object.fillCreateCommonField(owner);
            objects.insert(object);
        }
        return object;
    }

    /** 已存在对象建立新引用前也必须持锁并重新读取，防止与回收竞争。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public StorageObjectEntity hold(long id) {
        var object = objects.selectById(id);
        if (object == null) throw new IllegalArgumentException("文件对象不存在");
        objectLock.holdUntilTransactionCompletion(object.getBucket(), object.getObjectKey());
        object = objects.lockPresent(id);
        if (object == null) throw new IllegalArgumentException("文件对象已回收");
        return object;
    }
}
