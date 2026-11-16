package top.aiolife.record.mapper;

import org.apache.ibatis.annotations.*;

@Mapper
public interface AttachmentCollectionMapper {
    @Update("""
        UPDATE attachment SET is_deleted=1,is_public=0,update_time=CURRENT_TIMESTAMP
        WHERE is_deleted=0 AND biz_id IS NULL AND update_time<CURRENT_TIMESTAMP-INTERVAL '7 days'
        """)
    int expireUnbound();
    @Delete("""
        DELETE FROM import_job WHERE state IN ('READY','FAILED','CANCELLED')
          AND update_time<CURRENT_TIMESTAMP-INTERVAL '30 days'
          AND (state<>'FAILED' OR EXISTS(SELECT 1 FROM import_job newer
            WHERE newer.biz_type=import_job.biz_type AND newer.biz_id=import_job.biz_id
              AND newer.id>import_job.id AND newer.is_deleted=0))
        """)
    int purgeFinishedJobs();
}
