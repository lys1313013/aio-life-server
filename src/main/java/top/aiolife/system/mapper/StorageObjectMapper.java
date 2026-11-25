package top.aiolife.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.*;
import top.aiolife.system.pojo.entity.StorageObjectEntity;

@Mapper
public interface StorageObjectMapper extends BaseMapper<StorageObjectEntity> {
    @Select("SELECT COUNT(*) FROM storage_object WHERE bucket=#{bucket} AND object_key=#{key}")
    long countStoredObject(String bucket,String key);
    @Select("""
        <script>
        SELECT object_key FROM storage_object WHERE bucket=#{bucket} AND object_key IN
        <foreach collection="keys" item="key" open="(" separator="," close=")">#{key}</foreach>
        </script>
        """)
    List<String> findStoredKeys(String bucket, List<String> keys);
    @Select("SELECT * FROM storage_object WHERE id=#{id} AND is_deleted=0 FOR UPDATE")
    @Options(useCache=false,flushCache=Options.FlushCachePolicy.TRUE)
    StorageObjectEntity lockPresent(long id);
    @Select("SELECT o.* FROM storage_object o WHERE o.create_time<CURRENT_TIMESTAMP-INTERVAL '7 days' AND NOT EXISTS(SELECT 1 FROM attachment a WHERE a.storage_object_id=o.id AND a.is_deleted=0) AND NOT EXISTS(SELECT 1 FROM external_resource r WHERE r.storage_object_id=o.id AND r.is_deleted=0) ORDER BY o.id LIMIT 100")
    List<StorageObjectEntity> collectionCandidates();
    @Select("""
        SELECT (SELECT count(*) FROM attachment WHERE storage_object_id=#{id} AND is_deleted=0)
             + (SELECT count(*) FROM external_resource WHERE storage_object_id=#{id} AND is_deleted=0)
        """)
    @Options(useCache=false,flushCache=Options.FlushCachePolicy.TRUE)
    long references(long id);
    @Delete("DELETE FROM storage_object WHERE id=#{id}")
    int deletePhysical(long id);
}
