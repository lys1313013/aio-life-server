package top.aiolife.record.mapper;
import java.util.List;
import top.aiolife.record.util.DoubanSubjectUrl.MediaType;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import top.aiolife.record.pojo.entity.MovieEntity;

@Mapper
public interface IMovieMapper extends BaseMapper<MovieEntity> {
    static void filterRanking(LambdaQueryWrapper<MovieEntity> wrapper, Long listId) {
        if (listId != null) wrapper.exists("""
                SELECT 1 FROM ranking_list_item i JOIN ranking_list l ON l.id=i.ranking_list_id AND l.is_deleted=0
                JOIN creative_work w ON w.id=i.creative_work_id AND w.is_deleted=0
                WHERE w.resource_type={1} AND w.platform=movie.platform
                  AND w.external_key=movie.external_key AND i.is_deleted=0 AND l.id={0}
                """, listId, MediaType.MOVIE.resourceType());
    }

    /** 固定排序表达式只在 Mapper 中维护；ID 兜底保证跨页顺序稳定。 */
    static void applyPageOrder(LambdaQueryWrapper<MovieEntity> wrapper,
                               boolean inProgressFirst) {
        String statusOrder = inProgressFirst
                ? "WHEN 'in_progress' THEN 1 WHEN 'not_started' THEN 2"
                : "WHEN 'not_started' THEN 1 WHEN 'in_progress' THEN 2";
        wrapper.last("ORDER BY CASE status " + statusOrder
                + " WHEN 'completed' THEN 3 WHEN 'on_hold' THEN 4 ELSE 0 END,"
                + " finish_date DESC NULLS LAST, create_time DESC NULLS LAST, id DESC");
    }
    @Select("SELECT * FROM movie WHERE user_id=#{userId} AND id=#{id} AND is_deleted=0 FOR UPDATE")
    @Options(useCache=false, flushCache=Options.FlushCachePolicy.TRUE)
    MovieEntity captureLockOwned(@Param("userId") long userId,@Param("id") long id);
    @Select("SELECT * FROM movie WHERE user_id=#{userId} AND platform='douban' AND external_key=#{subjectId} AND is_deleted=0 FOR UPDATE")
    @Options(useCache=false, flushCache=Options.FlushCachePolicy.TRUE)
    List<MovieEntity> captureLockSubjects(@Param("userId") long userId,@Param("subjectId") String subjectId);
}