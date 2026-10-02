package top.aiolife.system.mapper;

import org.apache.ibatis.annotations.*;
import top.aiolife.system.pojo.dto.StorageFileReference;
import java.util.List;

@Mapper
public interface StorageFileReferenceMapper {
    /** 显式 SQL 保留软删除关联；普通 BaseMapper 查询会自动过滤 @TableLogic 字段。 */
    @Select("""
        SELECT id, file_name AS name, create_user AS owner, biz_type, biz_id, is_deleted AS deleted
        FROM file
        WHERE file_name IN (#{key}, #{basename}, #{bucketKey}, #{bucketBasename})
           OR file_name LIKE #{keySuffix} ESCAPE '!' OR file_name LIKE #{basenameSuffix} ESCAPE '!'
        """)
    @Options(useCache=false, flushCache=Options.FlushCachePolicy.TRUE)
    List<StorageFileReference> selectReferencesIncludingDeleted(
            @Param("key") String key, @Param("basename") String basename,
            @Param("bucketKey") String bucketKey, @Param("bucketBasename") String bucketBasename,
            @Param("keySuffix") String keySuffix, @Param("basenameSuffix") String basenameSuffix);
    /** CBTI 图片不写入 file 表，软删除人格仍保留引用保护。 */
    @Select("SELECT COUNT(*) FROM cbti_personality WHERE image_object = #{key} OR image_object = CONCAT('/', #{key})")
    @Options(useCache=false, flushCache=Options.FlushCachePolicy.TRUE)
    long countCbtiReferencesIncludingDeleted(@Param("key") String key);
}
