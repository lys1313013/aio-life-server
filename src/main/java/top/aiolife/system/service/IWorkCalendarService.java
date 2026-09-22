package top.aiolife.system.service;

import java.time.LocalDate;

/** 基于中国大陆工作日历的日期查询，包含法定放假及调休补班。 */
public interface IWorkCalendarService {
    /** 是否为工作日（类型 0、3）；日历缺失时返回 null，不按星期推测。 */
    Boolean isWorkday(LocalDate date);

    /**
     * 查找严格早于目标日期的最近同类日（工作日或非工作日）。
     * 目标日期或回溯途中的日期缺失时返回 null，避免跨过未知日期得出错误结果。
     * 以目标日期为 Redis key，成功结果缓存 2 天，缺失结果不缓存。
     */
    LocalDate findPreviousComparableDate(LocalDate date);

    /** 清除指定目标日期的参考日缓存，日历修正后可对受影响的目标日期调用。 */
    void evictPreviousComparableDate(LocalDate date);
}
