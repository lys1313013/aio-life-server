package top.aiolife.record.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.*;
import top.aiolife.record.pojo.entity.ExternalResourceEntity;

@Mapper
public interface ExternalResourceMapper extends BaseMapper<ExternalResourceEntity> {
    @Insert("""
        INSERT INTO external_resource(id,platform,resource_type,external_key,variant,source_url,state,attempts,
          create_user,update_user,create_time,update_time,is_deleted)
        VALUES(#{id},#{platform},#{resourceType},#{externalKey},#{variant},#{sourceUrl},'PENDING',0,
          #{createUser},#{updateUser},CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)
        ON CONFLICT(platform,resource_type,external_key,variant) DO UPDATE
          SET source_url=EXCLUDED.source_url
          WHERE external_resource.storage_object_id IS NULL AND external_resource.state<>'RUNNING'
        """)
    int register(ExternalResourceEntity resource);

    @Select("""
        SELECT * FROM external_resource WHERE platform=#{platform} AND resource_type=#{resourceType}
          AND external_key=#{externalKey} AND variant=#{variant} AND is_deleted=0
        """)
    @Options(useCache=false, flushCache=Options.FlushCachePolicy.TRUE)
    ExternalResourceEntity identity(ExternalResourceEntity resource);

    @Update("""
        UPDATE external_resource SET state='RUNNING',attempts=LEAST(attempts+1,5),lease_token=#{token},
          lease_until=CURRENT_TIMESTAMP+INTERVAL '120 seconds',update_time=CURRENT_TIMESTAMP
        WHERE id=#{id} AND is_deleted=0 AND storage_object_id IS NULL
          AND ((attempts<5 AND state IN ('PENDING','FAILED') AND (next_attempt_at IS NULL OR next_attempt_at<=CURRENT_TIMESTAMP))
            OR (state='RUNNING' AND lease_until<CURRENT_TIMESTAMP))
        """)
    int claim(long id, String token);

    @Select("SELECT * FROM external_resource WHERE id=#{id} AND lease_token=#{token} AND state='RUNNING' AND is_deleted=0 FOR UPDATE")
    @Options(useCache=false, flushCache=Options.FlushCachePolicy.TRUE)
    ExternalResourceEntity lockClaim(long id, String token);

    @Select("""
        SELECT r.* FROM external_resource r WHERE r.is_deleted=0 AND r.storage_object_id IS NULL
          AND ((r.state IN ('PENDING','FAILED') AND r.attempts<5 AND (r.next_attempt_at IS NULL OR r.next_attempt_at<=CURRENT_TIMESTAMP))
            OR (r.state='RUNNING' AND r.lease_until<CURRENT_TIMESTAMP))
          AND EXISTS(SELECT 1 FROM import_job j WHERE j.external_resource_id=r.id AND j.is_deleted=0 AND j.state IN ('PENDING','RUNNING'))
        ORDER BY r.update_time,r.id LIMIT 10
        """)
    List<ExternalResourceEntity> due();

    @Update("""
        UPDATE external_resource SET state='PENDING',attempts=0,next_attempt_at=NULL,error_code=NULL,
          source_url=#{url},lease_token=NULL,lease_until=NULL,update_time=CURRENT_TIMESTAMP
        WHERE id=#{id} AND storage_object_id IS NULL AND state='FAILED'
        """)
    int retry(long id, String url);
}
