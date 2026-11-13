package top.aiolife.record.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import top.aiolife.record.pojo.entity.BVideoEntity;
import top.aiolife.record.pojo.entity.DeviceEntity;
import top.aiolife.record.pojo.entity.MemoEntity;

/** 有界首页查询；归属、逻辑删除和筛选在数据库执行。 */
public interface HomeSectionMapper {
    @Select("""
        SELECT id, title, hidden_content, update_time FROM memo
        WHERE user_id = #{userId} AND is_deleted = 0
        ORDER BY update_time DESC NULLS LAST, id DESC LIMIT 3
        """)
    List<MemoEntity> recentMemos(@Param("userId") long userId);

    @Select("""
        SELECT id, title, url, bvid, current_episode, episodes, watched_duration_seconds, duration_seconds, last_watched, cover_file_id, cover_state
        FROM b_video WHERE user_id = #{userId} AND is_deleted = 0 AND status = 'in_progress'
        ORDER BY COALESCE(last_watched, update_time, create_time) DESC NULLS LAST, id DESC LIMIT 3
        """)
    List<BVideoEntity> continuingVideos(@Param("userId") long userId);

    @Select("""
        SELECT d.id, d.name, d.purchase_date, d.purchase_price, d.end_date,
               (SELECT f.id FROM file f WHERE f.biz_type='device' AND f.biz_id=d.id
                AND f.create_user=d.user_id AND f.is_deleted=0) AS file_id FROM device d
        WHERE user_id = #{userId} AND is_deleted = 0 AND status = '1'
        ORDER BY purchase_date DESC NULLS LAST, id DESC LIMIT 3
        """)
    List<DeviceEntity> activeDevices(@Param("userId") long userId);

}
