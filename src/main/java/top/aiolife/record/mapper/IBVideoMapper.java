package top.aiolife.record.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;
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
    @org.apache.ibatis.annotations.Select("SELECT * FROM b_video WHERE id=#{id} AND user_id=#{userId} AND is_deleted=0 FOR UPDATE")
    @org.apache.ibatis.annotations.Options(useCache=false, flushCache=org.apache.ibatis.annotations.Options.FlushCachePolicy.TRUE)
    BVideoEntity lockOwned(long id, long userId);


    @org.apache.ibatis.annotations.Select("SELECT COUNT(*) FROM b_video WHERE id=#{videoId} AND user_id=#{userId} AND cover_file_id=#{fileId} AND is_deleted=0")
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
