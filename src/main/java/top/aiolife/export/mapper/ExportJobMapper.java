package top.aiolife.export.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import top.aiolife.export.pojo.entity.ExportJobEntity;

import java.time.LocalDateTime;
import java.util.List;

public interface ExportJobMapper extends BaseMapper<ExportJobEntity> {
    @Select("SELECT * FROM data_export_job WHERE id = #{id} AND is_deleted = 0 FOR UPDATE")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    ExportJobEntity lock(@Param("id") long id);

    @Select(
            "SELECT COUNT(*) FROM data_export_job WHERE user_id = #{userId} AND quota_month ="
                    + " #{month} AND quota_charged = 1 AND is_deleted = 0")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    int monthlyUsed(@Param("userId") long userId, @Param("month") String month);

    @Select(
            "SELECT * FROM data_export_job WHERE status IN ('QUEUED','RUNNING') AND is_deleted = 0"
                    + " ORDER BY create_time, id LIMIT 102")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    List<ExportJobEntity> active();

    @Select("SELECT * FROM data_export_job WHERE id = #{id} AND is_deleted = 0")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    ExportJobEntity current(@Param("id") long id);

    @Update(
            "UPDATE data_export_job SET lease_until = #{until} WHERE id = #{id} AND status ="
                + " 'RUNNING' AND lease_token = #{token} AND lease_until > #{now} AND is_deleted ="
                + " 0")
    int heartbeat(
            @Param("id") long id,
            @Param("token") String token,
            @Param("now") LocalDateTime now,
            @Param("until") LocalDateTime until);
}
