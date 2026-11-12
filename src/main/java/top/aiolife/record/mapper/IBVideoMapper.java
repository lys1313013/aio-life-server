package top.aiolife.record.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import top.aiolife.record.pojo.entity.BVideoEntity;
import top.aiolife.record.pojo.vo.StatusCount;

import java.util.List;

/**
 * 类功能描述
 *
 * @author Lys
 * @date 2025/10/06 23:03
 */
public interface IBVideoMapper extends BaseMapper<BVideoEntity> {
    /**
     * 按用户和 BV 号读取一条有效视频记录；bvid 使用确定性排序，按精确值匹配。
     */
    @Select("""
            SELECT * FROM b_video
            WHERE user_id = #{userId} AND is_deleted = 0 AND bvid = #{bvid}
            ORDER BY update_time DESC NULLS LAST, id DESC
            LIMIT 1
            """)
    BVideoEntity findOwnedByBvid(@Param("userId") long userId, @Param("bvid") String bvid);

    /**
     * 写入视频；并发创建同一用户同一 BV 号时由唯一索引忽略后到记录。
     *
     * @return 1 表示已插入，0 表示已存在有效记录
     */
    @Insert("""
            INSERT INTO b_video (
              id, cover_file_id, cover_state, cover_version, title, url, cover, duration_seconds,
              watched_duration_seconds, episodes, current_episode, status, last_watched, notes,
              bvid, aid, description, owner_name, pages_info, user_id,
              create_user, create_time, update_user, update_time, is_deleted
            ) VALUES (
              #{id}, #{coverFileId}, COALESCE(#{coverState}, 'NONE'), COALESCE(#{coverVersion}, 0),
              #{title}, #{url}, #{cover}, #{durationSeconds},
              COALESCE(#{watchedDurationSeconds}, 0), #{episodes}, #{currentEpisode},
              COALESCE(#{status.code}, 'in_progress'), #{lastWatched}, #{notes},
              #{bvid}, #{aid}, #{description}, #{ownerName},
              CAST(#{pagesInfo} AS jsonb), #{userId},
              #{createUser}, #{createTime}, #{updateUser}, #{updateTime}, 0
            )
            ON CONFLICT DO NOTHING
            """)
    int insertIgnoreActive(BVideoEntity entity);

    @Select("SELECT * FROM b_video WHERE id=#{id} AND user_id=#{userId} AND is_deleted=0 FOR UPDATE")
    @Options(useCache=false, flushCache=Options.FlushCachePolicy.TRUE)
    BVideoEntity lockOwned(long id, long userId);


    @Select("SELECT COUNT(*) FROM b_video WHERE id=#{videoId} AND user_id=#{userId} AND cover_file_id=#{fileId} AND is_deleted=0")
    long countCoverReference(long videoId, long userId, String fileId);

    /**
     * 按业务状态顺序分页查询
     */
    default IPage<BVideoEntity> selectPageWithStatusOrder(
            Page<BVideoEntity> page, Wrapper<BVideoEntity> queryWrapper, List<String> statusCodes) {
        page.setCountId(IBVideoMapper.class.getName() + ".countWithStatusFilter");
        return selectPageWithStatusOrderRows(page, queryWrapper, statusCodes);
    }

    IPage<BVideoEntity> selectPageWithStatusOrderRows(
            Page<BVideoEntity> page,
            @Param(Constants.WRAPPER) Wrapper<BVideoEntity> queryWrapper,
            @Param("statusCodes") List<String> statusCodes);

    /**
     * 获取状态和数量
     */
    List<StatusCount> getStatusCount(Long userId);

    /**
     * 获取已学习时长
     */
    Integer getWatchTime(@Param("userId") Long userId,
                         @Param("completedStatus") String completedStatus);

    /**
     * 获取总时长
     */
    Integer getTotalTime(Long userId);
}
