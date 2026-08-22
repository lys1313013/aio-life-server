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
     * 获取历史上紧接在某个分类之后的最高频分类
     *
     * @param userId             用户id
     * @param previousCategoryId 上一个分类id
     * @param isWorkday          是否工作日
     * @return 分类id
     */
    Long getMostFrequentNextCategory(@org.apache.ibatis.annotations.Param("userId") long userId,
                                     @org.apache.ibatis.annotations.Param("previousCategoryId") Long previousCategoryId,
                                     @org.apache.ibatis.annotations.Param("isWorkday") boolean isWorkday);

    /**
     * 获取历史上同一时间点的最高频分类
     *
     * @param userId            用户id
     * @param time              时间
     * @param excludeCategoryId 要排除的分类id
     * @param isWorkday         是否工作日
     * @return 分类id
     */
    Long getMostFrequentCategoryAtTime(@org.apache.ibatis.annotations.Param("userId") long userId,
                                       @org.apache.ibatis.annotations.Param("time") int time,
                                       @org.apache.ibatis.annotations.Param("excludeCategoryId") Long excludeCategoryId,
                                       @org.apache.ibatis.annotations.Param("isWorkday") boolean isWorkday);

    /**
     * 推荐分类 (原有逻辑)
     *
     * @param userId 用户id
     * @param date   日期
     * @param time   时间
     * @return 记录实体
     */
    TimeRecordEntity recommendType(@org.apache.ibatis.annotations.Param("userId") long userId,
                                   @org.apache.ibatis.annotations.Param("date") String date,
                                   @org.apache.ibatis.annotations.Param("time") int time);
}
