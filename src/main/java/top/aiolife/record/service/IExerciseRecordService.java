package top.aiolife.record.service;

import com.baomidou.mybatisplus.extension.service.IService;
import java.time.LocalDate;
import java.util.List;
import top.aiolife.core.resq.PageResp;
import top.aiolife.record.pojo.dto.ExerciseStatisticsDTO;
import top.aiolife.record.pojo.entity.ExerciseRecordEntity;
import top.aiolife.record.pojo.query.ExerciseRecordQuery;
import top.aiolife.record.pojo.query.ExerciseStatisticsQuery;
import top.aiolife.record.pojo.vo.ExerciseDashboardSummaryVO;
import top.aiolife.record.pojo.vo.ExerciseStatisticsPointVO;

/**
 * 运动记录Service接口
 *
 * @author Lys
 * @date 2025-11-29 18:40
 */
public interface IExerciseRecordService extends IService<ExerciseRecordEntity> {

    /**
     * 获取今日运动种类
     *
     * @param userId 用户ID
     * @return 运动种类数量
     */
    int countTodayExerciseTypes(Long userId);

    /**
     * 获取连续运动天数
     *
     * @param userId 用户ID
     * @return 连续运动天数
     */
    int getConsecutiveExerciseDays(Long userId);

    /**
     * 获取首页运动汇总（按天 × 运动类型聚合），游标分页
     *
     * @param userId   用户ID
     * @param lastDate 下一页游标，传 null 时表示从今天（含）往后开始取
     * @param limit    每页返回的不重复日期数量上限
     * @return 汇总结果（含 hasMore / 下一游标）
     */
    ExerciseDashboardSummaryVO getDashboardSummary(Long userId, LocalDate lastDate, int limit);

    /**
     * 首页运动汇总：在服务内解析日期游标并收敛 limit，非法游标按默认值处理。
     */
    ExerciseDashboardSummaryVO dashboardSummary(long userId, String lastDate, Integer limit);

    /** 当前用户的运动记录分页，运动日期与 ID 倒序。 */
    PageResp<ExerciseRecordEntity> pageForUser(long userId, ExerciseRecordQuery filters, int page, int pageSize);

    /** 新增运动记录，用户归属和审计字段由服务端设置。 */
    boolean createForUser(long userId, ExerciseRecordEntity entity);

    /** 仅更新当前用户拥有的运动记录。 */
    boolean updateForUser(long userId, long id, ExerciseRecordEntity entity);

    /** 仅逻辑删除当前用户拥有的运动记录。 */
    void deleteForUser(long userId, List<Long> ids);

    /** 查询当前用户的运动记录，不存在时返回 null。 */
    ExerciseRecordEntity getOwnedOrNull(long userId, long id);

    /** 全历史按日期和类型聚合。 */
    List<ExerciseStatisticsPointVO> statisticsSummary(long userId, ExerciseStatisticsQuery filters);

    /** 统计明细，无筛选时取最近一年，最多 1000 条。 */
    List<ExerciseRecordEntity> statisticsList(long userId, ExerciseStatisticsQuery filters);

    /** 轻量统计明细，无筛选时取最近一年，最多 1000 条。 */
    List<ExerciseStatisticsDTO> lightStatistics(long userId, ExerciseStatisticsQuery filters);
}