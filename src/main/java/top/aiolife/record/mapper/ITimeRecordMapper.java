package top.aiolife.record.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import top.aiolife.record.pojo.entity.TimeRecordEntity;

/**
 * 类功能描述
 *
 * @author Lys
 * @date 2025/10/26 15:32
 */
public interface ITimeRecordMapper extends BaseMapper<TimeRecordEntity> {

    /**
     * 查询参考日覆盖目标分钟、且在指定截止分钟前结束的记录。
     * 返回列表以识别重叠；分类可见性由服务层统一判断。
     */
    java.util.List<TimeRecordEntity> findReferenceRecords(
            @org.apache.ibatis.annotations.Param("userId") long userId,
            @org.apache.ibatis.annotations.Param("date") String date,
            @org.apache.ibatis.annotations.Param("time") int time,
            @org.apache.ibatis.annotations.Param("endBefore") int endBefore);
}
