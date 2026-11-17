package top.aiolife.system.mapper;

import org.apache.ibatis.annotations.*;
import top.aiolife.system.pojo.dto.StorageFileReference;
import java.util.List;

@Mapper
public interface StorageFileReferenceMapper {
    /** 显式 SQL 保留软删除关联；普通 BaseMapper 查询会自动过滤 @TableLogic 字段。 */
    @Select("""
        SELECT a.file_key AS id,a.file_name AS name,a.user_id AS owner,a.biz_type,a.biz_id,a.is_deleted AS deleted
        FROM attachment a JOIN storage_object o ON o.id=a.storage_object_id
        WHERE o.object_key=#{key}
        """)
    @Options(useCache=false, flushCache=Options.FlushCachePolicy.TRUE)
    List<StorageFileReference> selectReferencesIncludingDeleted(@Param("key") String key);
}
