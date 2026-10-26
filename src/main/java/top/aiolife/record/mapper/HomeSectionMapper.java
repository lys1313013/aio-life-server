package top.aiolife.record.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import top.aiolife.record.pojo.entity.BVideoEntity;
import top.aiolife.record.pojo.entity.DeviceEntity;
import top.aiolife.record.pojo.entity.MemoEntity;

/** 有界首页查询；归属、逻辑删除和筛选在数据库执行，兼容 MySQL 5.7。 */
public interface HomeSectionMapper {
    @Select("""
        SELECT id, title, hidden_content, update_time FROM memo
        WHERE user_id = #{userId} AND is_deleted = 0
        ORDER BY update_time DESC, id DESC LIMIT 3
        """)
    List<MemoEntity> recentMemos(@Param("userId") long userId);

    @Select("""
        SELECT id, title, url, bvid, current_episode, episodes, watched_duration, duration, last_watched, cover_file_id, cover_state
        FROM b_video WHERE user_id = #{userId} AND is_deleted = 0 AND status = 'in_progress'
        ORDER BY COALESCE(last_watched, update_time, create_time) DESC, id DESC LIMIT 3
        """)
    List<BVideoEntity> continuingVideos(@Param("userId") long userId);

    @Select("""
        SELECT id, name, purchase_date, purchase_price, end_date, file_id FROM device
        WHERE user_id = #{userId} AND is_deleted = 0 AND status = '1'
        ORDER BY purchase_date DESC, id DESC LIMIT 3
        """)
    List<DeviceEntity> activeDevices(@Param("userId") long userId);

}
