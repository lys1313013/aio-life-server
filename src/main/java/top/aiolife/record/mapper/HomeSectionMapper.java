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
        SELECT v.id, v.title, v.url, v.bvid, v.current_episode, v.episodes,
               v.watched_duration_seconds, v.duration_seconds, v.last_watched,
               COALESCE((SELECT CASE WHEN j.state IN ('PENDING','RUNNING') THEN 'PENDING' WHEN j.state='FAILED' THEN 'FAILED' ELSE NULL END
                 FROM import_job j WHERE j.biz_type='b_video_cover' AND j.biz_id=v.id AND j.is_deleted=0 ORDER BY j.create_time DESC,j.id DESC LIMIT 1),
                 CASE WHEN EXISTS(SELECT 1 FROM attachment a WHERE a.biz_type='b_video_cover' AND a.biz_id=v.id AND a.is_deleted=0) THEN 'READY' ELSE 'NONE' END) AS cover_state,
               (SELECT f.file_key FROM attachment f WHERE f.biz_type='b_video_cover' AND f.biz_id=v.id
                AND f.user_id=v.user_id AND f.is_deleted=0) AS cover_file_id
        FROM b_video v WHERE v.user_id = #{userId} AND v.is_deleted = 0 AND v.status = 'in_progress'
        ORDER BY COALESCE(v.last_watched, v.update_time, v.create_time) DESC NULLS LAST, v.id DESC LIMIT 3
        """)
    List<BVideoEntity> continuingVideos(@Param("userId") long userId);

    @Select("""
        SELECT d.id, d.name, d.purchase_date, d.purchase_price, d.end_date,
               (SELECT f.file_key FROM attachment f WHERE f.biz_type='device_cover' AND f.biz_id=d.id
                AND f.user_id=d.user_id AND f.is_deleted=0) AS file_id FROM device d
        WHERE user_id = #{userId} AND is_deleted = 0 AND status = '1'
        ORDER BY purchase_date DESC NULLS LAST, id DESC LIMIT 3
        """)
    List<DeviceEntity> activeDevices(@Param("userId") long userId);

}
