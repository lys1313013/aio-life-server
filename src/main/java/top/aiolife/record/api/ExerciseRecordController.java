package top.aiolife.record.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.aiolife.core.id.IdCodec;
import top.aiolife.core.query.CommonQuery;
import top.aiolife.core.query.QueryParams;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.resq.PageResp;
import top.aiolife.core.web.CurrentUserId;
import top.aiolife.record.convertor.RecordApiConvertor;
import top.aiolife.record.pojo.dto.ExerciseStatisticsDTO;
import top.aiolife.record.pojo.entity.ExerciseRecordEntity;
import top.aiolife.record.pojo.query.ExerciseRecordQuery;
import top.aiolife.record.pojo.query.ExerciseStatisticsQuery;
import top.aiolife.record.pojo.req.CommonReq;
import top.aiolife.record.pojo.req.ExerciseRecordCreateReq;
import top.aiolife.record.pojo.req.ExerciseRecordUpdateReq;
import top.aiolife.record.pojo.vo.ExerciseDashboardSummaryVO;
import top.aiolife.record.pojo.vo.ExerciseRecordVO;
import top.aiolife.record.pojo.vo.ExerciseStatisticsPointVO;
import top.aiolife.record.service.IExerciseRecordService;

/**
 * 运动记录控制器
 *
 * @author Lys
 * @date 2025-11-29 18:40
 */
@Tag(name = "运动记录")
@RestController
@AllArgsConstructor
@RequestMapping("/exerciseRecord")
public class ExerciseRecordController {

    private final IExerciseRecordService exerciseRecordService;

    @Operation(summary = "分页查询运动记录")
    @GetMapping("/query")
    public ApiResponse<PageResp<ExerciseRecordVO>> query(
            @CurrentUserId String userId,
            @QueryParams CommonQuery<ExerciseRecordQuery> requestQuery) {
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toExerciseRecordVOPage(
                exerciseRecordService.pageForUser(IdCodec.decode(userId), requestQuery.getCondition(),
                        requestQuery.getPage(), requestQuery.getPageSize())));
    }

    @Operation(summary = "新增运动记录")
    @PostMapping
    public ApiResponse<Boolean> add(@CurrentUserId String userId,
                                    @Valid @RequestBody ExerciseRecordCreateReq exerciseRecordReq) {
        ExerciseRecordEntity entity = RecordApiConvertor.INSTANCE.fromExerciseRecordCreateReq(exerciseRecordReq);
        return ApiResponse.success(exerciseRecordService.createForUser(IdCodec.decode(userId), entity));
    }

    @Operation(summary = "更新运动记录")
    @PutMapping("/{id}")
    public ApiResponse<Boolean> update(@CurrentUserId String userId, @PathVariable String id,
                                       @Valid @RequestBody ExerciseRecordUpdateReq exerciseRecordReq) {
        ExerciseRecordEntity entity = RecordApiConvertor.INSTANCE.fromExerciseRecordUpdateReq(exerciseRecordReq);
        return ApiResponse.success(exerciseRecordService.updateForUser(
                IdCodec.decode(userId), IdCodec.decode(id), entity));
    }

    @Operation(summary = "批量删除运动记录")
    @PostMapping("/deleteBatch")
    public ApiResponse<Boolean> deleteBatch(@CurrentUserId String userId,
                                            @Valid @RequestBody CommonReq commonReq) {
        exerciseRecordService.deleteForUser(IdCodec.decode(userId), IdCodec.decodeAll(commonReq.getIdList()));
        return ApiResponse.success();
    }

    @Operation(summary = "查询运动记录详情")
    @GetMapping("/get/{id}")
    public ApiResponse<ExerciseRecordVO> get(@CurrentUserId String userId, @PathVariable String id) {
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toExerciseRecordVO(
                exerciseRecordService.getOwnedOrNull(IdCodec.decode(userId), IdCodec.decode(id))));
    }

    /** 完整运动统计：未指定日期时统计全部历史，同日同类型合并为一个运动次数。 */
    @Operation(summary = "按日期和类型汇总运动量")
    @GetMapping("/statistics/summary")
    public ApiResponse<List<ExerciseStatisticsPointVO>> getStatisticsSummary(
            @CurrentUserId String userId,
            @QueryParams ExerciseStatisticsQuery params) {
        return ApiResponse.success(exerciseRecordService.statisticsSummary(IdCodec.decode(userId), params));
    }

    /**
     * 获取运动记录统计数据（限制时间范围和数据量），用于统计图表
     * 返回业务字段，不包含所属用户或审计字段
     */
    @Operation(summary = "查询运动统计明细", description = "无筛选时取最近一年；最多返回 1000 条，完整汇总请使用 statistics/summary。")
    @GetMapping("/statistics")
    public ApiResponse<List<ExerciseRecordVO>> getStatistics(
            @CurrentUserId String userId,
            @QueryParams ExerciseStatisticsQuery params) {
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toExerciseRecordVOList(
                exerciseRecordService.statisticsList(IdCodec.decode(userId), params)));
    }

    @Operation(summary = "查询轻量运动统计明细", description = "无筛选时取最近一年；最多返回 1000 条，仅含统计字段。")
    @GetMapping("/statistics/light")
    public ApiResponse<List<ExerciseStatisticsDTO>> getLightStatistics(
            @CurrentUserId String userId,
            @QueryParams ExerciseStatisticsQuery params) {
        return ApiResponse.success(exerciseRecordService.lightStatistics(IdCodec.decode(userId), params));
    }

    /**
     * 首页运动汇总：按天 × 运动类型聚合，支持游标翻页（向下滚动加载历史）
     *
     * @param lastDate 上一页返回的最早日期的前一天；首次传 null 表示从今天（含）开始
     * @param limit    每页返回的不重复日期数量上限（默认 7）
     */
    @Operation(summary = "分页查询每日运动汇总")
    @GetMapping("/dashboardSummary")
    public ApiResponse<ExerciseDashboardSummaryVO> getDashboardSummary(
            @CurrentUserId String userId,
            @RequestParam(required = false) String lastDate,
            @RequestParam(required = false, defaultValue = "7") Integer limit) {
        return ApiResponse.success(exerciseRecordService.dashboardSummary(
                IdCodec.decode(userId), lastDate, limit));
    }
}
