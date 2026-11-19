package top.aiolife.record.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import top.aiolife.record.pojo.vo.RankingOptionVO;
import top.aiolife.record.pojo.entity.RankingListEntity;

@Mapper
public interface RankingListMapper extends BaseMapper<RankingListEntity> {
    @Select("""
        SELECT l.id,l.name FROM ranking_list l WHERE l.is_deleted=0 AND EXISTS (
          SELECT 1 FROM ranking_list_item i JOIN creative_work w ON w.id=i.creative_work_id AND w.is_deleted=0
          WHERE i.ranking_list_id=l.id AND i.is_deleted=0 AND
            ((#{media}='book' AND w.kind='book') OR (#{media}='movie' AND w.kind IN ('film','series'))))
        ORDER BY l.name,l.id
        """)
    List<RankingOptionVO> options(String media);
}
