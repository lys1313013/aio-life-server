package top.aiolife.record.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.aiolife.core.id.IdCodec;
import top.aiolife.core.query.CommonQuery;
import top.aiolife.core.query.QueryParams;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.web.CurrentUserId;
import top.aiolife.record.convertor.RecordApiConvertor;
import top.aiolife.record.mcp.req.TimeRecordDateRangeMcpReq;
import top.aiolife.record.pojo.entity.TimeRecordEntity;
import top.aiolife.record.pojo.query.TimeRecordQuery;
import top.aiolife.record.pojo.query.TimeWeekQuery;
import top.aiolife.record.pojo.req.TimeRecordDeleteByDateReq;
import top.aiolife.record.pojo.req.TimeRecordReq;
import top.aiolife.record.pojo.req.TimeRecordSaveReq;
import top.aiolife.record.pojo.req.TimeRecordUpdateReq;
import top.aiolife.record.pojo.vo.TimeRecordDateRangeVO;
import top.aiolife.record.pojo.vo.TimeRecordListVO;
import top.aiolife.record.pojo.vo.TimeRecordRecommendationVO;
import top.aiolife.record.pojo.vo.TimeRecordVO;
import top.aiolife.record.service.ITimeRecordService;

/**
 * 类功能描述
 *
 * @author Lys
 * @date 2025/10/25 23:16
 */
@Tag(name = "时迹 时间记录 时间块")
@RestController
@AllArgsConstructor
@RequestMapping("/timeRecord")
public class TimeRecordController {

    private final ITimeRecordService timeRecordService;

    /** 查询当前用户指定日期的全部记录，不分页。 */
    @Operation(summary = "查询指定日期的时迹")
    @GetMapping("/query")
    public ApiResponse<List<TimeRecordListVO>> query(@CurrentUserId String userId,
                                                     @Valid @QueryParams TimeRecordQuery requestQuery) {
        return ApiResponse.success(timeRecordService.queryDay(
                IdCodec.decode(userId), requestQuery.getDate()));
    }

    /**
     * 查询指定日期的记录
     * @param query 查询参数
     */
    @Operation(summary = "查询日期范围内的时迹")
    @GetMapping("/queryByDateRange")
    public ApiResponse<List<TimeRecordListVO>> queryByDateRange(
            @CurrentUserId String userId,
            @QueryParams CommonQuery<TimeWeekQuery> query) {
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toTimeRecordListVOList(
                timeRecordService.listByDateRange(IdCodec.decode(userId), query.getCondition())));
    }

    /**
     * 查询指定日期的记录（格式化时间返回）
     * @param req 查询参数（包含startDate和endDate）
     */
    @Operation(summary = "查询时迹及格式化时间")
    @GetMapping("/queryByDateRangeForAI")
    public ApiResponse<List<TimeRecordDateRangeVO>> queryByDateRangeForAI(
            @CurrentUserId String userId,
            @QueryParams TimeRecordDateRangeMcpReq req) {
        return ApiResponse.success(timeRecordService.queryByDateRangeForAI(
                IdCodec.decode(userId), req.getStartDate(), req.getEndDate()));
    }

    /**
     * 根据 id 查询
     * @param id id
     */
    @Operation(summary = "查询时迹详情")
    @GetMapping("/{id}")
    public ApiResponse<TimeRecordVO> getById(@CurrentUserId String userId, @PathVariable("id") String id) {
        return ApiResponse.success(timeRecordService.getDetail(IdCodec.decode(userId), IdCodec.decode(id)));
    }

    @PostMapping
    @Operation(summary = "新增时迹")
    public ApiResponse<String> save(@CurrentUserId String userId,
                                    @Valid @RequestBody TimeRecordSaveReq request) {
        TimeRecordReq timeRecordReq = RecordApiConvertor.INSTANCE.fromTimeRecordSaveReq(request);
        return ApiResponse.success(
                timeRecordService.saveTimeRecord(IdCodec.decode(userId), timeRecordReq));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新时迹")
    public ApiResponse<Boolean> update(@CurrentUserId String userId, @PathVariable("id") String id,
                                       @Valid @RequestBody TimeRecordUpdateReq request) {
        TimeRecordReq timeRecordReq = RecordApiConvertor.INSTANCE.fromTimeRecordUpdateReq(request);
        timeRecordReq.setId(id);
        timeRecordService.updateTimeRecord(IdCodec.decode(userId), timeRecordReq);
        return ApiResponse.success();
    }

    /**
     * 删除
     * @param entity id
     */
    @Operation(summary = "删除时迹")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@CurrentUserId String userId, @PathVariable("id") String id) {
        timeRecordService.removeById(IdCodec.decode(id), IdCodec.decode(userId));
        return ApiResponse.success();
    }

    /**
     * 删除
     * @param entity id
     */
    @Operation(summary = "删除指定日期的时迹")
    @PostMapping("/deleteByDate")
    public ApiResponse<Void> deleteByDay(@CurrentUserId String userId,
                                         @Valid @RequestBody TimeRecordDeleteByDateReq entityReq) {
        TimeRecordEntity entity = RecordApiConvertor.INSTANCE.fromTimeRecordDeleteByDateReq(entityReq);
        timeRecordService.removeByDate(entity.getDate(), IdCodec.decode(userId));
        return ApiResponse.success();
    }

    /**
     * 为当前登录用户推荐指定日期、指定时刻的时迹分类，不创建或修改时间记录。
     *
     * <p>参考最近一个同类日（工作日或非工作日）覆盖该时刻的记录。
     * 日历缺失或无法得到有效分类时返回空字符串。</p>
     *
     * @param date 目标日期，格式 yyyy-MM-dd，例如 2026-09-25
     * @param time 目标时刻距当天 00:00 的分钟数，范围 0～1439，例如 600 表示 10:00；不是时长或时间戳
     * @return 成功响应的 data 为分类 ID 字符串；无推荐时 data 为 ""，仍返回成功码 "0"
     */
    @Operation(summary = "推荐指定时刻的时迹分类")
    @GetMapping("/recommendType")
    public ApiResponse<String> recommendType(@CurrentUserId String userId, String date, int time) {
        // 分类 ID 按字符串返回，避免前端 JavaScript Number 丢失大整数精度。
        Long categoryId = timeRecordService.recommendType(IdCodec.decode(userId), date, time);
        return ApiResponse.success(categoryId == null ? "" : String.valueOf(categoryId));
    }

    /**
     * 推荐下一个时间块
     * @param date 日期 yyyy-MM-dd
     */
    @Operation(summary = "推荐下一个时迹时间块")
    @GetMapping("/recommendNext")
    public ApiResponse<TimeRecordRecommendationVO> recommendNext(@CurrentUserId String userId, String date) {
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toTimeRecordRecommendationVO(
                timeRecordService.recommendNextWithCategory(IdCodec.decode(userId), date)));
    }

    /**
     * 获取关联业务类型列表
     */
    @Operation(summary = "查询时迹可关联业务类型")
    @GetMapping("/relateTypes")
    public ApiResponse<List<Map<String, Object>>> getRelateTypes() {
        return ApiResponse.success(timeRecordService.listRelateTypes());
    }
}
