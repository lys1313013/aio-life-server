package top.aiolife.record.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.*;
import top.aiolife.record.pojo.entity.ImageImportTaskEntity;

@Mapper
public interface ImageImportTaskMapper extends BaseMapper<ImageImportTaskEntity> {
    @Select("""
        SELECT j.* FROM import_job j JOIN external_resource r ON r.id=j.external_resource_id
        WHERE j.is_deleted=0 AND j.state IN ('PENDING','RUNNING')
          AND (r.state='READY' OR (r.state='FAILED' AND r.attempts>=5))
        ORDER BY j.update_time,j.id LIMIT 100
        """)
    List<ImageImportTaskEntity> due();

    @Select("SELECT * FROM import_job WHERE id=#{id} AND state IN ('PENDING','RUNNING') AND is_deleted=0 FOR UPDATE")
    @Options(useCache=false, flushCache=Options.FlushCachePolicy.TRUE)
    ImageImportTaskEntity lockActive(long id);

    @Update("""
        UPDATE import_job SET state='CANCELLED',update_user=#{owner},update_time=CURRENT_TIMESTAMP
        WHERE biz_type=#{type} AND biz_id=#{id} AND is_deleted=0 AND state IN ('PENDING','RUNNING')
        """)
    int cancel(String type, long id, long owner);

    @Select("SELECT * FROM import_job WHERE biz_type=#{type} AND biz_id=#{id} AND is_deleted=0 ORDER BY create_time DESC,id DESC LIMIT 1")
    ImageImportTaskEntity latest(String type, long id);
    @Select("""
        <script>SELECT DISTINCT ON (biz_id) * FROM import_job
        WHERE biz_type='b_video_cover' AND is_deleted=0 AND biz_id IN
        <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
        ORDER BY biz_id,create_time DESC,id DESC</script>
        """)
    List<ImageImportTaskEntity> latestFor(@Param("ids") java.util.Collection<Long> ids);
}
