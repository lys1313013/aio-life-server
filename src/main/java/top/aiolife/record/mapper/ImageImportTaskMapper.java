package top.aiolife.record.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;
import top.aiolife.record.pojo.entity.ImageImportTaskEntity;
import java.util.List;

@Mapper
public interface ImageImportTaskMapper extends BaseMapper<ImageImportTaskEntity> {
    @Select("""
        SELECT * FROM image_import_task WHERE is_deleted=0
        AND ((state='PENDING' AND attempts < 5 AND next_attempt_at <= CURRENT_TIMESTAMP)
             OR (state='RUNNING' AND lease_until < CURRENT_TIMESTAMP))
        ORDER BY next_attempt_at, id LIMIT 10
        """)
    List<ImageImportTaskEntity> due();

    @Update("""
        UPDATE image_import_task SET state='RUNNING', attempts=LEAST(attempts+1,5),
          lease_token=#{token}, lease_until=TIMESTAMPADD(SECOND, 120, CURRENT_TIMESTAMP),
          update_time=CURRENT_TIMESTAMP
        WHERE id=#{id} AND is_deleted=0
          AND ((state='PENDING' AND attempts < 5 AND next_attempt_at <= CURRENT_TIMESTAMP)
               OR (state='RUNNING' AND lease_until < CURRENT_TIMESTAMP))
        """)
    int claim(long id, String token);

    @Select("SELECT * FROM image_import_task WHERE id=#{id} AND lease_token=#{token} AND state='RUNNING' FOR UPDATE")
    @Options(useCache=false, flushCache=Options.FlushCachePolicy.TRUE)
    ImageImportTaskEntity lockClaim(long id, String token);
}
