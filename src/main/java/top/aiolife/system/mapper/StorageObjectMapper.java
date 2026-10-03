package top.aiolife.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import top.aiolife.system.pojo.entity.StorageObjectEntity;

@Mapper
public interface StorageObjectMapper extends BaseMapper<StorageObjectEntity> {
    // 管理删除保护包括软删除对象，避免误删仍需恢复的引用。
    @Select("SELECT COUNT(*) FROM storage_object WHERE bucket = #{bucket} AND object_key = #{key}")
    long countStoredObject(String bucket, String key);
}
