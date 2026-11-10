package top.aiolife.scheduling.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import top.aiolife.scheduling.pojo.entity.ScheduledJobLogEntity;

@Mapper
public interface ScheduledJobLogMapper extends BaseMapper<ScheduledJobLogEntity> {
    /** 冲突时保留原记录，不让 PostgreSQL 的重复键异常中止外层事务。 */
    @Insert("""
        INSERT INTO sys_scheduled_job_log (id, job_id, trigger_key, trigger_type, scheduled_time, start_time, end_time, duration_ms, status, message, instance_id, is_deleted, create_user, create_time, update_user, update_time)
        VALUES (#{id}, #{jobId}, #{triggerKey}, #{triggerType}, #{scheduledTime}, #{startTime}, #{endTime}, #{durationMs}, #{status}, #{message}, #{instanceId}, #{isDeleted}, #{createUser}, #{createTime}, #{updateUser}, #{updateTime})
        ON CONFLICT (job_id, trigger_key) DO NOTHING
        """)
    int insertIfAbsent(ScheduledJobLogEntity entity);
}
