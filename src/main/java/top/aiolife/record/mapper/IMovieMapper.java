package top.aiolife.record.mapper;

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
                ? "'in_progress', 'not_started', 'completed', 'on_hold'"
                : "'not_started', 'in_progress', 'completed', 'on_hold'";
        wrapper.last("ORDER BY FIELD(status, " + statusOrder + "), finish_time DESC, create_time DESC, id DESC");
    }
}