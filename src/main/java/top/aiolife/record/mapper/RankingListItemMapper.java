package top.aiolife.record.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import top.aiolife.record.pojo.entity.RankingListItemEntity;
import top.aiolife.record.pojo.vo.RankingBadgeVO;

@Mapper
public interface RankingListItemMapper extends BaseMapper<RankingListItemEntity> {
    @Select("""
        <script>
        SELECT i.creative_work_id, l.id AS ranking_list_id, l.name, i.rank
        FROM ranking_list_item i JOIN ranking_list l ON l.id=i.ranking_list_id AND l.is_deleted=0
        JOIN creative_work w ON w.id=i.creative_work_id AND w.is_deleted=0
        WHERE i.is_deleted=0 AND i.creative_work_id IN
        <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
        ORDER BY l.name, l.id
        </script>
        """)
    List<RankingBadgeVO> badges(Collection<Long> ids);
}
