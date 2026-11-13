package top.aiolife.record.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 单图业务归属检查与行锁；表名只从固定分支选择。 */
@Mapper
public interface RecordImageMapper {
    @Select("""
        <script>
        SELECT id FROM
        <choose>
          <when test="bizType == 'device'">device</when>
          <when test="bizType == 'movie'">movie</when>
          <when test="bizType == 'read'">read_record</when>
          <when test="bizType == 'wardrobe_item'">wardrobe_item</when>
        </choose>
        WHERE id=#{bizId} AND user_id=#{userId} AND is_deleted=0 FOR UPDATE
        </script>
        """)
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    Long lockOwned(@Param("userId") long userId, @Param("bizType") String bizType,
                   @Param("bizId") long bizId);
}
