package top.aiolife.record.api;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import top.aiolife.core.query.CommonQuery;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.resq.PageResp;
import top.aiolife.record.pojo.entity.ExerciseRecordEntity;
import top.aiolife.record.pojo.entity.TimeRecordEntity;
import top.aiolife.record.pojo.entity.UserDictDataEntity;
import top.aiolife.record.pojo.enums.RelateTypeEnum;
import top.aiolife.record.enums.DictTypeEnum;
import top.aiolife.record.pojo.query.TimeWeekQuery;
import top.aiolife.record.pojo.req.TimeRecordReq;
import top.aiolife.record.mcp.req.TimeRecordDateRangeMcpReq;
import top.aiolife.record.convertor.TimeRecordConvertor;
import top.aiolife.record.pojo.vo.RecommendNextVO;
import top.aiolife.record.pojo.vo.TimeRecordDateRangeVO;
import top.aiolife.record.pojo.vo.TimeRecordExerciseVO;
import top.aiolife.record.pojo.vo.TimeRecordVO;
import top.aiolife.record.service.IExerciseRecordService;
import top.aiolife.record.service.ITimeRecordService;
import top.aiolife.record.service.ITimeTrackerCategoryService;
import top.aiolife.record.service.UserDictDataService;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 类功能描述
 *
 * @author Lys
 * @date 2025/10/25 23:16
 */
@Slf4j
@RestController
@AllArgsConstructor
@RequestMapping("/timeRecord")
public class TimeRecordController {
    private final ITimeRecordService timeRecordService;
    private final IExerciseRecordService exerciseRecordService;
    private final ITimeTrackerCategoryService timeTrackerCategoryService;
    private final UserDictDataService userDictDataService;

    public ITimeRecordService getBaseMapper() {
        return timeRecordService;
    }

    @PostMapping("/query")
    public ApiResponse<PageResp<TimeRecordEntity>> query(
            @RequestBody CommonQuery<TimeRecordEntity> query) {
        long userId = StpUtil.getLoginIdAsLong();
        LambdaQueryWrapper<TimeRecordEntity> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.select(
                TimeRecordEntity::getId,
                TimeRecordEntity::getDate,
                TimeRecordEntity::getCategoryId,
                TimeRecordEntity::getStartTime,
                TimeRecordEntity::getEndTime,
                TimeRecordEntity::getTitle,
                TimeRecordEntity::getRelateId,
                TimeRecordEntity::getRelateType);
        lambdaQueryWrapper.eq(TimeRecordEntity::getUserId, userId);
        TimeRecordEntity condition = query.getCondition();
        lambdaQueryWrapper.eq(TimeRecordEntity::getDate, condition.getDate());

        lambdaQueryWrapper.orderByDesc(TimeRecordEntity::getUpdateTime);
        Page<TimeRecordEntity> page = new Page<>(query.getPage(), query.getPageSize());
        IPage<TimeRecordEntity> iPage = timeRecordService.page(page, lambdaQueryWrapper);
        PageResp<TimeRecordEntity> objectPageResp = PageResp.of(iPage.getRecords(), iPage.getTotal());
        return ApiResponse.success(objectPageResp);
    }

    /**
     * 查询指定日期的记录
     * @param query 查询参数
     */
    @PostMapping("/queryByDateRange")
    public ApiResponse<List<TimeRecordEntity>> queryByDateRange(
            @RequestBody CommonQuery<TimeWeekQuery> query) {
        List<TimeRecordEntity> list = queryByDateRangeList(query);
        return ApiResponse.success(list);
    }

    /**
     * 查询指定日期的记录（格式化时间返回）
     * @param req 查询参数（包含startDate和endDate）
     */
    @PostMapping("/queryByDateRangeForAI")
    public ApiResponse<List<TimeRecordDateRangeVO>> queryByDateRangeForAI(
            @RequestBody TimeRecordDateRangeMcpReq req) {
        long userId = StpUtil.getLoginIdAsLong();
        List<TimeRecordEntity> list = queryByDateRangeForAIList(userId, req);
        List<TimeRecordDateRangeVO> voList = TimeRecordConvertor.INSTANCE.toDateRangeVOList(list);

        Set<Long> categoryIds = list.stream()
                .map(TimeRecordEntity::getCategoryId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());

        Map<Long, String> categoryNameMap = Collections.emptyMap();
        if (!categoryIds.isEmpty()) {
            categoryNameMap = timeTrackerCategoryService.listByIds(categoryIds).stream()
                    .collect(Collectors.toMap(
                            top.aiolife.record.pojo.entity.TimeTrackerCategoryEntity::getId,
                            top.aiolife.record.pojo.entity.TimeTrackerCategoryEntity::getName
                    ));
        }

        for (int i = 0; i < voList.size(); i++) {
            Long categoryId = list.get(i).getCategoryId();
            if (categoryId != null && categoryNameMap.containsKey(categoryId)) {
                voList.get(i).setCategoryName(categoryNameMap.get(categoryId));
            }
            if ("".equals(list.get(i).getTitle())) {
                voList.get(i).setTitle(null);
            }
        }

        fillExerciseDetails(userId, list, voList);

        // 按日期和时间从晚到早排序
        voList.sort(Comparator
                .comparing(TimeRecordDateRangeVO::getDate, Comparator.reverseOrder())
                .thenComparing(TimeRecordDateRangeVO::getStartTime, Comparator.reverseOrder()));

        return ApiResponse.success(voList);
    }

    /**
     * 批量填充运动明细，仅向 AI 接口暴露运动名称和次数。
     */
    private void fillExerciseDetails(long userId, List<TimeRecordEntity> records,
                                     List<TimeRecordDateRangeVO> voList) {
        Set<String> timeIds = records.stream()
                .map(TimeRecordEntity::getId)
                .filter(id -> id != null && !id.isEmpty())
                .collect(Collectors.toSet());
        if (timeIds.isEmpty()) {
            return;
        }

        List<ExerciseRecordEntity> exerciseRecords = exerciseRecordService.lambdaQuery()
                .select(ExerciseRecordEntity::getTimeId,
                        ExerciseRecordEntity::getExerciseTypeId,
                        ExerciseRecordEntity::getExerciseCount)
                .eq(ExerciseRecordEntity::getUserId, userId)
                .in(ExerciseRecordEntity::getTimeId, timeIds)
                .list();
        if (exerciseRecords.isEmpty()) {
            return;
        }

        Map<Long, String> exerciseNameMap = userDictDataService
                .listUserVisibleDictData(userId, DictTypeEnum.EXERCISE_TYPE.getValue(), true)
                .stream()
                .filter(dict -> dict.getId() != null && dict.getDictLabel() != null)
                .collect(Collectors.toMap(
                        UserDictDataEntity::getId,
                        UserDictDataEntity::getDictLabel,
                        (existing, replacement) -> replacement));

        Map<String, List<TimeRecordExerciseVO>> exercisesByTimeId = exerciseRecords.stream()
                .collect(Collectors.groupingBy(
                        ExerciseRecordEntity::getTimeId,
                        Collectors.mapping(exercise -> new TimeRecordExerciseVO(
                                exerciseNameMap.getOrDefault(
                                        exercise.getExerciseTypeId(), "未知运动"),
                                exercise.getExerciseCount()), Collectors.toList())));

        for (int i = 0; i < records.size(); i++) {
            List<TimeRecordExerciseVO> exercises = exercisesByTimeId.get(records.get(i).getId());
            if (exercises != null && !exercises.isEmpty()) {
                voList.get(i).setExercises(exercises);
            }
        }
    }

    /**
     * 根据日期范围查询记录（AI接口专用）
     */
    private List<TimeRecordEntity> queryByDateRangeForAIList(long userId, TimeRecordDateRangeMcpReq req) {
        LambdaQueryWrapper<TimeRecordEntity> lambdaQueryWrapper = buildDateRangeForAIQueryWrapper(userId, req);
        return timeRecordService.list(lambdaQueryWrapper);
    }

    /**
     * 构建日期范围查询条件（AI接口专用）
     */
    private LambdaQueryWrapper<TimeRecordEntity> buildDateRangeForAIQueryWrapper(long userId, TimeRecordDateRangeMcpReq req) {
        LambdaQueryWrapper<TimeRecordEntity> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.select(TimeRecordEntity::getId,
                TimeRecordEntity::getCategoryId,
                TimeRecordEntity::getDate,
                TimeRecordEntity::getStartTime,
                TimeRecordEntity::getEndTime,
                TimeRecordEntity::getTitle);
        lambdaQueryWrapper.eq(TimeRecordEntity::getUserId, userId);
        lambdaQueryWrapper.between(TimeRecordEntity::getDate, req.getStartDate(), req.getEndDate());
        lambdaQueryWrapper.orderByDesc(TimeRecordEntity::getUpdateTime);
        return lambdaQueryWrapper;
    }

    /**
     * 复用查询逻辑：根据日期范围查询记录
     */
    private List<TimeRecordEntity> queryByDateRangeList(CommonQuery<TimeWeekQuery> query) {
        long userId = StpUtil.getLoginIdAsLong();
        LambdaQueryWrapper<TimeRecordEntity> lambdaQueryWrapper = buildDateRangeQueryWrapper(userId, query);
        return timeRecordService.list(lambdaQueryWrapper);
    }

    /**
     * 构建日期范围查询条件
     */
    private LambdaQueryWrapper<TimeRecordEntity> buildDateRangeQueryWrapper(long userId, CommonQuery<TimeWeekQuery> query) {
        LambdaQueryWrapper<TimeRecordEntity> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.select(TimeRecordEntity::getId,
                TimeRecordEntity::getCategoryId,
                TimeRecordEntity::getDate,
                TimeRecordEntity::getStartTime,
                TimeRecordEntity::getEndTime,
                TimeRecordEntity::getTitle,
                TimeRecordEntity::getRelateId,
                TimeRecordEntity::getRelateType);
        lambdaQueryWrapper.eq(TimeRecordEntity::getUserId, userId);
        TimeWeekQuery condition = query.getCondition();
        lambdaQueryWrapper.between(TimeRecordEntity::getDate, condition.getStartDate(), condition.getEndDate());
        lambdaQueryWrapper.orderByDesc(TimeRecordEntity::getUpdateTime);
        return lambdaQueryWrapper;
    }

    /**
     * 根据 id 查询
     * @param id id
     */
    @GetMapping("/{id}")
    public ApiResponse<TimeRecordVO> getById(@PathVariable("id") String id) {
        long userId = StpUtil.getLoginIdAsLong();
        LambdaQueryWrapper<TimeRecordEntity> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(TimeRecordEntity::getId, id);
        lambdaQueryWrapper.eq(TimeRecordEntity::getUserId, userId);
        TimeRecordEntity entity = timeRecordService.getOne(lambdaQueryWrapper);
        if (entity == null) {
            return ApiResponse.success(null);
        }

        TimeRecordVO vo = new TimeRecordVO();
        BeanUtil.copyProperties(entity, vo);

        List<ExerciseRecordEntity> exercises = exerciseRecordService.lambdaQuery()
                .eq(ExerciseRecordEntity::getTimeId, id)
                .eq(ExerciseRecordEntity::getUserId, userId)
                .list();
        vo.setExercises(exercises);

        return ApiResponse.success(vo);
    }

    @PostMapping
    public ApiResponse<Boolean> save(@RequestBody TimeRecordReq timeRecordReq) {
        timeRecordService.saveTimeRecord(timeRecordReq);
        return ApiResponse.success();
    }

    @PutMapping("/{id}")
    public ApiResponse<Boolean> update(@PathVariable("id") String id, @RequestBody TimeRecordReq timeRecordReq) {
        timeRecordReq.setId(id);
        timeRecordService.updateTimeRecord(timeRecordReq);
        return ApiResponse.success();
    }

    /**
     * 删除
     * @param entity id
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable("id") String id) {
        timeRecordService.removeById(id, StpUtil.getLoginIdAsLong());
        return ApiResponse.success();
    }

    /**
     * 删除
     * @param entity id
     */
    @PostMapping("/deleteByDate")
    public ApiResponse<Void> deleteByDay(@RequestBody TimeRecordEntity entity) {
        timeRecordService.removeByDate(entity.getDate(), StpUtil.getLoginIdAsLong());
        return ApiResponse.success();
    }

    /**
     * 推荐分类
     * @param date 日期
     * @param time 时间
     * @param previousCategoryId 紧邻的上一条记录分类id（可选）
     * @return 分类id
     */
    @GetMapping("/recommendType")
    public ApiResponse<String> recommendType(String date, int time, @RequestParam(required = false) String previousCategoryId) {
        long userId = StpUtil.getLoginIdAsLong();
        Long categoryId = timeRecordService.recommendType(userId, date, time, toCategoryId(previousCategoryId));
        if (categoryId == null) {
            return ApiResponse.success("");
        }
        return ApiResponse.success(String.valueOf(categoryId));
    }

    /**
     * 推荐下一个时间块
     * @param date 日期 yyyy-MM-dd
     */
    @GetMapping("/recommendNext")
    public ApiResponse<RecommendNextVO> recommendNext(String date) {
        long userId = StpUtil.getLoginIdAsLong();
        RecommendNextVO result = timeRecordService.recommendNext(userId, date);
        
        // 获取推荐分类
        TimeRecordEntity recommend = result.getRecommend();
        
        // 寻找紧邻的上一条记录分类
        Long previousCategoryId = null;
        if (result.getRecords() != null) {
            for (TimeRecordEntity record : result.getRecords()) {
                if (record.getEndTime() != null && record.getEndTime() == recommend.getStartTime() - 1) {
                    previousCategoryId = record.getCategoryId();
                    break;
                }
            }
        }

        Long categoryId = timeRecordService.recommendType(userId, date, recommend.getStartTime(), previousCategoryId);
        recommend.setCategoryId(categoryId);

        return ApiResponse.success(result);
    }

    /**
     * 将前端传入的分类标识（数字字符串）转为 Long；空串 / 非数字视为未指定。
     */
    private Long toCategoryId(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 获取关联业务类型列表
     */
    @GetMapping("/relateTypes")
    public ApiResponse<List<Map<String, Object>>> getRelateTypes() {
        return ApiResponse.success(RelateTypeEnum.toList());
    }
}
