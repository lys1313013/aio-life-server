package top.aiolife.scheduling.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import top.aiolife.scheduling.pojo.entity.ScheduledJobEntity;

@Mapper
public interface ScheduledJobMapper extends BaseMapper<ScheduledJobEntity> {
    /** 冲突时保留原记录，不让 PostgreSQL 的重复键异常中止外层事务。 */
    @Insert("""
        INSERT INTO sys_scheduled_job (id, job_code, name, cron, zone_id, is_enabled, revision, is_deleted, create_user, create_time, update_user, update_time)
        VALUES (#{id}, #{jobCode}, #{name}, #{cron}, #{zoneId}, #{isEnabled}, #{revision}, #{isDeleted}, #{createUser}, #{createTime}, #{updateUser}, #{updateTime})
        ON CONFLICT (job_code) WHERE is_deleted = 0 DO NOTHING
        """)
    int insertIfAbsent(ScheduledJobEntity entity);
}
