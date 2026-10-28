package top.aiolife.record.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
     * 使用 idx_bvid 缩小范围，再按字节精确比较 BV 号，避免默认排序规则忽略大小写。
     * 重复标记沿列表状态顺序、更新时间取首条，ID 用于同一更新时间的稳定排序。
     */
    @Select("""
            SELECT * FROM b_video
            WHERE user_id = #{userId} AND is_deleted = 0 AND bvid = #{bvid}
              AND CAST(bvid AS BINARY(20)) = CAST(#{bvid} AS BINARY(20))
            ORDER BY CASE status
                WHEN 'not_started' THEN 1 WHEN 'in_progress' THEN 2
                WHEN 'on_hold' THEN 3 WHEN 'completed' THEN 4 ELSE 0 END,
                update_time DESC, id DESC
            LIMIT 1
            """)
    BVideoEntity findOwnedByBvid(@Param("userId") long userId, @Param("bvid") String bvid);

    @Select("SELECT * FROM b_video WHERE id=#{id} AND user_id=#{userId} AND is_deleted=0 FOR UPDATE")
    @Options(useCache=false, flushCache=Options.FlushCachePolicy.TRUE)
    BVideoEntity lockOwned(long id, long userId);


    @Select("SELECT COUNT(*) FROM b_video WHERE id=#{videoId} AND user_id=#{userId} AND cover_file_id=#{fileId} AND is_deleted=0")
    long countCoverReference(long videoId, long userId, String fileId);

    /**
     * 按业务状态顺序分页查询
     */
    IPage<BVideoEntity> selectPageWithStatusOrder(
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
