package top.aiolife.record.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;
import top.aiolife.record.pojo.entity.FileEntity;

@Mapper
public interface IFileMapper extends BaseMapper<FileEntity> {
    String WITH_OBJECT="SELECT a.*,o.file_size,o.content_type AS file_type,o.content_hash AS hash_value FROM attachment a JOIN storage_object o ON o.id=a.storage_object_id";
    @Select(WITH_OBJECT+" WHERE a.file_key=#{key} AND a.is_deleted=0 FOR UPDATE OF a")
    @Options(useCache=false,flushCache=Options.FlushCachePolicy.TRUE)
    FileEntity selectForBindingByKey(@Param("key") String key);
    @Select(WITH_OBJECT+" WHERE a.id=#{id} AND a.is_deleted=0")
    FileEntity selectWithObject(@Param("id") String id);
    @Select(WITH_OBJECT+" WHERE a.file_key=#{key} AND a.is_deleted=0")
    FileEntity selectWithObjectByKey(@Param("key") String key);
    @Select("""
        <script>SELECT id FROM
        <choose>
          <when test="type=='feedback'">feedback</when>
          <when test="type=='feedback_comment'">feedback_comment</when>
          <when test="type=='honor_record'">honor_record</when>
          <otherwise>event_record</otherwise>
        </choose>
        WHERE id=#{bizId} AND user_id=#{owner} AND is_deleted=0 FOR UPDATE</script>
        """)
    @Options(useCache=false,flushCache=Options.FlushCachePolicy.TRUE)
    Long lockBusiness(String type,long bizId,long owner);
}
