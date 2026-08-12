package top.aiolife.record.api;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import top.aiolife.core.query.CommonQuery;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.resq.PageResp;
import top.aiolife.core.util.SysUtil;
import top.aiolife.record.mapper.IExerciseRecordMapper;
import top.aiolife.record.pojo.dto.ExerciseStatisticsDTO;
import top.aiolife.record.pojo.entity.ExerciseRecordEntity;
import top.aiolife.record.pojo.req.CommonReq;
import top.aiolife.record.pojo.vo.ExerciseDashboardSummaryVO;
import top.aiolife.record.service.IExerciseRecordService;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j

/**
 * 运动记录控制器
 *
 * @author Lys
 * @date 2025-11-29 18:40
 */
@RestController
@AllArgsConstructor
@RequestMapping("/exerciseRecord")
public class ExerciseRecordController {

    private final IExerciseRecordMapper exerciseRecordMapper;

    private final IExerciseRecordService exerciseRecordService;

    public IExerciseRecordMapper getBaseMapper() {
        return exerciseRecordMapper;
    }

    /**
     * 查询运动记录列表
     */
    @PostMapping("/query")
    public ApiResponse<PageResp<ExerciseRecordEntity>> query(
            @RequestBody CommonQuery<ExerciseRecordEntity> query) {
        Long userId = StpUtil.getLoginIdAsLong();
        LambdaQueryWrapper<ExerciseRecordEntity> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(ExerciseRecordEntity::getUserId, userId);
        ExerciseRecordEntity condition = query.getCondition();
        lambdaQueryWrapper.eq(SysUtil.isNotEmpty(condition.getExerciseTypeId()), ExerciseRecordEntity::getExerciseTypeId, 
                condition.getExerciseTypeId());
//        lambdaQueryWrapper.eq(SysUtil.isNotEmpty(condition.getExerciseDate()), ExerciseRecordEntity::getExerciseDate,
//                condition.getExerciseDate());
        lambdaQueryWrapper.orderByDesc(ExerciseRecordEntity::getExerciseDate, ExerciseRecordEntity::getCreateTime);
        // 分页
        Page<ExerciseRecordEntity> page = new Page<>(query.getPage(), query.getPageSize());
        IPage<ExerciseRecordEntity> iPage = getBaseMapper().selectPage(page, lambdaQueryWrapper);
        PageResp<ExerciseRecordEntity> pageResp = PageResp.of(iPage.getRecords(), iPage.getTotal());
        return ApiResponse.success(pageResp);
    }

    /**
     * 新增运动记录
     */
    @PostMapping
    public ApiResponse<Boolean> add(@RequestBody ExerciseRecordEntity exerciseRecord) {
        Long userId = StpUtil.getLoginIdAsLong();
        exerciseRecord.setUserId(userId);
        exerciseRecord.setCreateUser(userId);
        return ApiResponse.success(getBaseMapper().insert(exerciseRecord) > 0);
    }

    /**
     * 修改运动记录
     */
    @PutMapping("/{id}")
    public ApiResponse<Boolean> update(@PathVariable("id") Long id, @RequestBody ExerciseRecordEntity exerciseRecord) {
        long userId = StpUtil.getLoginIdAsLong();
        exerciseRecord.setId(id);
        exerciseRecord.setUserId(null);
        exerciseRecord.setUpdateUser(userId);
        
        LambdaQueryWrapper<ExerciseRecordEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExerciseRecordEntity::getId, exerciseRecord.getId());
        wrapper.eq(ExerciseRecordEntity::getUserId, userId);
        
        return ApiResponse.success(getBaseMapper().update(exerciseRecord, wrapper) > 0);
    }

    // 批量删除
    @PostMapping("/deleteBatch")
    public ApiResponse<Boolean> deleteBatch(@RequestBody CommonReq commonReq) {
        LambdaQueryWrapper<ExerciseRecordEntity> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(ExerciseRecordEntity::getUserId, StpUtil.getLoginIdAsLong());
        lambdaQueryWrapper.in(ExerciseRecordEntity::getId, commonReq.getIdList());
        exerciseRecordMapper.delete(lambdaQueryWrapper);
        return ApiResponse.success();
    }

    /**
     * 获取运动记录详情
     */
    @GetMapping("/get/{id}")
    public ApiResponse<ExerciseRecordEntity> get(@PathVariable Long id) {
        LambdaQueryWrapper<ExerciseRecordEntity> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(ExerciseRecordEntity::getUserId, StpUtil.getLoginIdAsLong());
        lambdaQueryWrapper.eq(ExerciseRecordEntity::getId, id);
        return ApiResponse.success(getBaseMapper().selectOne(lambdaQueryWrapper));
    }

    /**
     * 获取运动记录统计数据（限制时间范围和数据量），用于统计图表
     * 返回完整的实体对象
     */
    @PostMapping("/statistics")
    public ApiResponse<List<ExerciseRecordEntity>> getStatistics(@RequestBody(required = false) Map<String, Object> params) {
        Long userId = StpUtil.getLoginIdAsLong();
        LambdaQueryWrapper<ExerciseRecordEntity> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(ExerciseRecordEntity::getUserId, userId);

        // 处理查询条件
        if (params != null) {
            // 运动类型
            String exerciseTypeId = (String) params.get("exerciseTypeId");
            lambdaQueryWrapper.eq(SysUtil.isNotEmpty(exerciseTypeId), ExerciseRecordEntity::getExerciseTypeId, exerciseTypeId);

            // 日期区间
            String startDate = (String) params.get("startDate");
            String endDate = (String) params.get("endDate");
            if (SysUtil.isNotEmpty(startDate)) {
                lambdaQueryWrapper.ge(ExerciseRecordEntity::getExerciseDate, java.time.LocalDate.parse(startDate));
            }
            if (SysUtil.isNotEmpty(endDate)) {
                lambdaQueryWrapper.le(ExerciseRecordEntity::getExerciseDate, java.time.LocalDate.parse(endDate));
            }
        } else {
            // 默认限制时间范围为最近一年
            LocalDate oneYearAgo = LocalDate.now().minusYears(1);
            lambdaQueryWrapper.ge(ExerciseRecordEntity::getExerciseDate, oneYearAgo);
        }

        // 限制返回数据量，避免性能问题
        lambdaQueryWrapper.orderByDesc(ExerciseRecordEntity::getExerciseDate, ExerciseRecordEntity::getCreateTime);
        lambdaQueryWrapper.last("LIMIT 1000"); // 限制最多返回1000条记录

        List<ExerciseRecordEntity> list = getBaseMapper().selectList(lambdaQueryWrapper);
        return ApiResponse.success(list);
    }

    /**
     * 获取轻量级运动记录统计数据，仅包含统计所需字段
     */
    @PostMapping("/statistics/light")
    public ApiResponse<List<ExerciseStatisticsDTO>> getLightStatistics(@RequestBody(required = false) Map<String, Object> params) {
        Long userId = StpUtil.getLoginIdAsLong();
        LambdaQueryWrapper<ExerciseRecordEntity> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(ExerciseRecordEntity::getUserId, userId);

        // 处理查询条件
        if (params != null) {
            // 运动类型
            String exerciseTypeId = (String) params.get("exerciseTypeId");
            lambdaQueryWrapper.eq(SysUtil.isNotEmpty(exerciseTypeId), ExerciseRecordEntity::getExerciseTypeId, exerciseTypeId);

            // 日期区间
            String startDate = (String) params.get("startDate");
            String endDate = (String) params.get("endDate");
            if (SysUtil.isNotEmpty(startDate)) {
                lambdaQueryWrapper.ge(ExerciseRecordEntity::getExerciseDate, java.time.LocalDate.parse(startDate));
            }
            if (SysUtil.isNotEmpty(endDate)) {
                lambdaQueryWrapper.le(ExerciseRecordEntity::getExerciseDate, java.time.LocalDate.parse(endDate));
            }
        } else {
            // 默认限制时间范围为最近一年
            LocalDate oneYearAgo = LocalDate.now().minusYears(1);
            lambdaQueryWrapper.ge(ExerciseRecordEntity::getExerciseDate, oneYearAgo);
        }

        // 限制返回数据量，避免性能问题
        lambdaQueryWrapper.orderByDesc(ExerciseRecordEntity::getExerciseDate, ExerciseRecordEntity::getCreateTime);
        lambdaQueryWrapper.last("LIMIT 1000"); // 限制最多返回1000条记录

        List<ExerciseRecordEntity> entities = getBaseMapper().selectList(lambdaQueryWrapper);

        // 转换为轻量级DTO
        List<ExerciseStatisticsDTO> dtos = entities.stream().map(entity -> {
            ExerciseStatisticsDTO dto = new ExerciseStatisticsDTO();
            dto.setExerciseTypeId(entity.getExerciseTypeId());
            dto.setExerciseDate(entity.getExerciseDate());
            dto.setExerciseCount(entity.getExerciseCount());
            dto.setDescription(entity.getDescription());
            return dto;
        }).collect(Collectors.toList());

        return ApiResponse.success(dtos);
    }

    /**
     * 首页运动汇总：按天 × 运动类型聚合，支持游标翻页（向下滚动加载历史）
     *
     * @param lastDate 上一页返回的最早日期的前一天；首次传 null 表示从今天（含）开始
     * @param limit    每页返回的不重复日期数量上限（默认 7）
     */
    @GetMapping("/dashboardSummary")
    public ApiResponse<ExerciseDashboardSummaryVO> getDashboardSummary(
            @RequestParam(required = false) String lastDate,
            @RequestParam(required = false, defaultValue = "7") Integer limit) {
        long userId = StpUtil.getLoginIdAsLong();
        LocalDate cursor = null;
        if (SysUtil.isNotEmpty(lastDate)) {
            try {
                cursor = LocalDate.parse(lastDate);
            } catch (Exception e) {
                log.warn("解析 lastDate 失败，使用默认值: {}", lastDate, e);
            }
        }
        int safeLimit = (limit == null || limit <= 0 || limit > 30) ? 7 : limit;
        return ApiResponse.success(exerciseRecordService.getDashboardSummary(userId, cursor, safeLimit));
    }
}