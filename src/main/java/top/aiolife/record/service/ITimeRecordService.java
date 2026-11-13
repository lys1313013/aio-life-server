package top.aiolife.record.service;

import com.baomidou.mybatisplus.extension.service.IService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import top.aiolife.record.pojo.entity.TimeRecordEntity;
import top.aiolife.record.pojo.query.TimeWeekQuery;
import top.aiolife.record.pojo.req.TimeRecordReq;
import top.aiolife.record.pojo.vo.RecommendNextVO;
import top.aiolife.record.pojo.vo.TimeRecordDateRangeVO;
import top.aiolife.record.pojo.vo.TimeRecordListVO;
import top.aiolife.record.pojo.vo.TimeRecordVO;

/**
 * 时间记录Service接口
 *
 * @author Lys
 * @date 2026-01-10 23:55
 */
public interface ITimeRecordService extends IService<TimeRecordEntity> {

    /** 查询指定用户的完整单日列表，缓存只用于非事务查询。 */
    List<TimeRecordListVO> queryDay(long userId, LocalDate date);

    /** 查询日期范围内的时迹，返回列表展示字段。 */
    List<TimeRecordEntity> listByDateRange(long userId, TimeWeekQuery condition);

    /** 查询日期范围内的时迹，并补充分类名称、运动明细和格式化时间。 */
    List<TimeRecordDateRangeVO> queryByDateRangeForAI(long userId, LocalDate startDate, LocalDate endDate);

    /** 查询当前用户的一条时迹详情及运动明细；不存在时返回 null。 */
    TimeRecordVO getDetail(long userId, Long id);

    /** 查询当前用户指定日期最后一条时迹的结束分钟，没有记录时返回 null。 */
    Integer findLatestEndMinute(long userId, LocalDate date);

    /**
     * 保存时间记录
     *
     * @param userId 当前用户 ID
     * @param timeRecordReq 请求参数
     */
    String saveTimeRecord(long userId, TimeRecordReq timeRecordReq);

    /**
     * 更新时间记录
     *
     * @param userId 当前用户 ID
     * @param timeRecordReq 请求参数
     */
    void updateTimeRecord(long userId, TimeRecordReq timeRecordReq);

    void removeById(Long id, long userId);

    /**
     * 根据日期删除记录及其关联的运动记录
     *
     * @param date   日期
     * @param userId 用户ID
     */
    void removeByDate(LocalDate date, long userId);

    /**
     * 根据用户的历史时间记录，为指定日期、指定时刻推荐时迹分类。
     *
     * <p>采用最近一个同类日（工作日或非工作日）覆盖目标时刻的唯一有效记录的分类。
     * 该方法只计算推荐，不保存时间记录。</p>
     *
     * @param userId             用户 ID，仅使用该用户可见的分类和该用户的历史记录
     * @param date               目标日期，格式 yyyy-MM-dd
     * @param time               目标时刻距当天 00:00 的分钟数，范围 0～1439，例如 600 表示 10:00
     * @return 推荐分类 ID；目标日期的日历缺失，或参考日兜底未得到有效分类时返回 null
     * @throws IllegalArgumentException time 不在 0～1439 范围内
     * @throws java.time.format.DateTimeParseException date 无法解析为日期
     */
    Long recommendType(long userId, String date, int time);

    /**
     * 推荐下一个时间块
     * @param userId 用户ID
     * @param date 日期 yyyy-MM-dd
     * @return 推荐结果及当日记录；当天没有剩余分钟时 recommend 为 null
     */
    RecommendNextVO recommendNext(long userId, String date);

    /** 推荐下一个时间块，并为推荐结果补充分类。 */
    RecommendNextVO recommendNextWithCategory(long userId, String date);

    /** 时迹可关联的业务类型。 */
    List<Map<String, Object>> listRelateTypes();
}
