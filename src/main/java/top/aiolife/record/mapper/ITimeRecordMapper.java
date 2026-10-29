package top.aiolife.record.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import java.util.List;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import top.aiolife.record.pojo.entity.TimeRecordEntity;

/**
 * 类功能描述
 *
 * @author Lys
 * @date 2025/10/26 15:32
 */
public interface ITimeRecordMapper extends BaseMapper<TimeRecordEntity> {

    /** 串行化同一时迹的局部更新，合并时间时必须读取最新的有效记录。 */
    @Select("SELECT * FROM time_record WHERE id = #{id} AND user_id = #{userId} AND is_deleted = 0 FOR UPDATE")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    TimeRecordEntity lockOwned(@Param("id") String id, @Param("userId") long userId);

    /**
     * 查询参考日覆盖目标分钟、且在指定截止分钟前结束的记录。
     * 返回列表以识别重叠；分类可见性由服务层统一判断。
     */
    List<TimeRecordEntity> findReferenceRecords(
            @Param("userId") long userId,
            @Param("date") String date,
            @Param("time") int time,
            @Param("endBefore") int endBefore);
}
