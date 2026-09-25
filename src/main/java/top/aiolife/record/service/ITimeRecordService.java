package top.aiolife.record.service;

import com.baomidou.mybatisplus.extension.service.IService;
import top.aiolife.record.pojo.entity.TimeRecordEntity;
import top.aiolife.record.pojo.vo.RecommendNextVO;

import top.aiolife.record.pojo.req.TimeRecordReq;

import java.time.LocalDate;

/**
 * 时间记录Service接口
 *
 * @author Lys
 * @date 2026-01-10 23:55
 */
public interface ITimeRecordService extends IService<TimeRecordEntity> {

    /**
     * 保存时间记录
     *
     * @param timeRecordReq 请求参数
     */
    String saveTimeRecord(TimeRecordReq timeRecordReq);

    /**
     * 更新时间记录
     *
     * @param timeRecordReq 请求参数
     */
    void updateTimeRecord(TimeRecordReq timeRecordReq);

    void removeById(String id, long userId);

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
     * <p>优先使用 Jev AI 推荐；未得到分类时，尝试采用最近一个同类日（工作日或非工作日）
     * 覆盖目标时刻的唯一有效记录的分类。该方法只计算推荐，不保存时间记录。</p>
     *
     * @param userId             用户 ID，仅使用该用户可见的分类和该用户的历史记录
     * @param date               目标日期，格式 yyyy-MM-dd
     * @param time               目标时刻距当天 00:00 的分钟数，范围 0～1439，例如 600 表示 10:00
     * @param previousCategoryId 紧邻上一条记录的分类 ID，可为 null；仅保留接口兼容，当前不参与计算或排除候选分类
     * @return 推荐分类 ID；目标日期的日历缺失，或 AI 与参考日兜底均未得到有效分类时返回 null
     * @throws IllegalArgumentException time 不在 0～1439 范围内
     * @throws java.time.format.DateTimeParseException date 无法解析为日期
     */
    Long recommendType(long userId, String date, int time, Long previousCategoryId);

    /**
     * 推荐下一个时间块
     * @param userId 用户ID
     * @param date 日期 yyyy-MM-dd
     * @return 推荐结果及当日记录；当天没有剩余分钟时 recommend 为 null
     */
    RecommendNextVO recommendNext(long userId, String date);
}
