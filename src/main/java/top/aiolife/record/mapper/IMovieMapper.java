package top.aiolife.record.mapper;
import java.util.List;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import top.aiolife.record.pojo.entity.MovieEntity;

@Mapper
public interface IMovieMapper extends BaseMapper<MovieEntity> {
    /** 固定排序表达式只在 Mapper 中维护；ID 兜底保证跨页顺序稳定。 */
    static void applyPageOrder(LambdaQueryWrapper<MovieEntity> wrapper,
                               boolean inProgressFirst) {
        String statusOrder = inProgressFirst
                ? "WHEN 'in_progress' THEN 1 WHEN 'not_started' THEN 2"
                : "WHEN 'not_started' THEN 1 WHEN 'in_progress' THEN 2";
        wrapper.last("ORDER BY CASE status " + statusOrder
                + " WHEN 'completed' THEN 3 WHEN 'on_hold' THEN 4 ELSE 0 END,"
                + " finish_time DESC NULLS LAST, create_time DESC NULLS LAST, id DESC");
    }
    @Select("SELECT * FROM movie WHERE user_id=#{userId} AND id=#{id} AND is_deleted=0 FOR UPDATE")
    @Options(useCache=false, flushCache=Options.FlushCachePolicy.TRUE)
    MovieEntity captureLockOwned(@Param("userId") long userId,@Param("id") long id);
    @Select("SELECT * FROM movie WHERE user_id=#{userId} AND url LIKE CONCAT('%/subject/',CAST(#{subjectId} AS text),'%') AND is_deleted=0 FOR UPDATE")
    @Options(useCache=false, flushCache=Options.FlushCachePolicy.TRUE)
    List<MovieEntity> captureLockSubjects(@Param("userId") long userId,@Param("subjectId") String subjectId);
}