package top.aiolife.record.service.impl;

import jakarta.annotation.Resource;
import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.aiolife.core.id.IdCodec;
import top.aiolife.record.convertor.RecordApiConvertor;
import top.aiolife.record.convertor.TimeRecordConvertor;
import top.aiolife.record.enums.DictTypeEnum;
import top.aiolife.record.mapper.ITimeRecordMapper;
import top.aiolife.record.pojo.entity.*;
import top.aiolife.record.pojo.enums.ProgressStatusEnum;
import top.aiolife.record.pojo.enums.RelateTypeEnum;
import top.aiolife.record.pojo.query.TimeWeekQuery;
import top.aiolife.record.pojo.req.ExerciseRecordReq;
import top.aiolife.record.pojo.req.TimeRecordReq;
import top.aiolife.record.pojo.vo.RecommendNextVO;
import top.aiolife.record.pojo.vo.TimeRecordDateRangeVO;
import top.aiolife.record.pojo.vo.TimeRecordExerciseVO;
import top.aiolife.record.pojo.vo.TimeRecordListVO;
import top.aiolife.record.pojo.vo.TimeRecordVO;
import top.aiolife.record.prediction.RecommendationDataCache;
import top.aiolife.record.service.*;
import top.aiolife.sso.service.SecondaryLockGuard;
import top.aiolife.system.service.IWorkCalendarService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 时间记录Service实现类
 *
 * @author Lys
 * @date 2026-01-10 23:55
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TimeRecordServiceImpl extends ServiceImpl<ITimeRecordMapper, TimeRecordEntity> implements ITimeRecordService {

    @Resource(name = "cacheManager")
    private CacheManager timeRecordCacheManager;
    private final ITimeRecordMapper timeRecordMapper;
    private final IExerciseRecordService exerciseRecordService;
    private final IReadRecordService readRecordService;
    private final IMovieService movieService;
    private final IWorkCalendarService workCalendarService;
    private final RecommendationDataCache recommendationDataCache;
    private final ITimeTrackerCategoryService timeTrackerCategoryService;
    private final SecondaryLockGuard secondaryLockGuard;
    private final UserDictDataService userDictDataService;

    @Override
    @Cacheable(
            cacheNames = "timeRecordDay:v1", cacheManager = "cacheManager",
            key = "#userId + ':' + #date",
            condition = "!T(org.springframework.transaction.support.TransactionSynchronizationManager).isActualTransactionActive()")
    public List<TimeRecordListVO> queryDay(long userId, LocalDate date) {
        Objects.requireNonNull(date, "date");
        List<TimeRecordEntity> records = timeRecordMapper.selectList(new LambdaQueryWrapper<TimeRecordEntity>()
                .select(TimeRecordEntity::getId, TimeRecordEntity::getDate,
                        TimeRecordEntity::getCategoryId, TimeRecordEntity::getStartMinute,
                        TimeRecordEntity::getEndMinute, TimeRecordEntity::getTitle,
                        TimeRecordEntity::getRelateId, TimeRecordEntity::getRelateType)
                .eq(TimeRecordEntity::getUserId, userId)
                .eq(TimeRecordEntity::getDate, date)
                .orderByDesc(TimeRecordEntity::getUpdateTime, TimeRecordEntity::getId));
        return RecordApiConvertor.INSTANCE.toTimeRecordListVOList(records);
    }

    @Override
    public List<TimeRecordEntity> listByDateRange(long userId, TimeWeekQuery condition) {
        LambdaQueryWrapper<TimeRecordEntity> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.select(TimeRecordEntity::getId,
                TimeRecordEntity::getCategoryId,
                TimeRecordEntity::getDate,
                TimeRecordEntity::getStartMinute,
                TimeRecordEntity::getEndMinute,
                TimeRecordEntity::getTitle,
                TimeRecordEntity::getRelateId,
                TimeRecordEntity::getRelateType);
        lambdaQueryWrapper.eq(TimeRecordEntity::getUserId, userId);
        lambdaQueryWrapper.between(TimeRecordEntity::getDate, condition.getStartDate(), condition.getEndDate());
        lambdaQueryWrapper.orderByDesc(TimeRecordEntity::getUpdateTime);
        return this.list(lambdaQueryWrapper);
    }

    @Override
    public List<TimeRecordDateRangeVO> queryByDateRangeForAI(long userId, LocalDate startDate, LocalDate endDate) {
        LambdaQueryWrapper<TimeRecordEntity> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.select(TimeRecordEntity::getId,
                TimeRecordEntity::getCategoryId,
                TimeRecordEntity::getDate,
                TimeRecordEntity::getStartMinute,
                TimeRecordEntity::getEndMinute,
                TimeRecordEntity::getTitle);
        lambdaQueryWrapper.eq(TimeRecordEntity::getUserId, userId);
        lambdaQueryWrapper.between(TimeRecordEntity::getDate, startDate, endDate);
        lambdaQueryWrapper.orderByDesc(TimeRecordEntity::getUpdateTime);
        List<TimeRecordEntity> list = this.list(lambdaQueryWrapper);
        List<TimeRecordDateRangeVO> voList = TimeRecordConvertor.INSTANCE.toDateRangeVOList(list);

        Set<Long> categoryIds = list.stream()
                .map(TimeRecordEntity::getCategoryId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());

        Map<Long, String> categoryNameMap = Collections.emptyMap();
        if (!categoryIds.isEmpty()) {
            categoryNameMap = timeTrackerCategoryService.list(new LambdaQueryWrapper<TimeTrackerCategoryEntity>()
                            .in(TimeTrackerCategoryEntity::getId, categoryIds)
                            .and(w -> w.eq(TimeTrackerCategoryEntity::getUserId, userId)
                                    .or().eq(TimeTrackerCategoryEntity::getUserId, 0L))).stream()
                    .collect(Collectors.toMap(
                            TimeTrackerCategoryEntity::getId,
                            TimeTrackerCategoryEntity::getName
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
                .thenComparing(TimeRecordDateRangeVO::getStartMinute, Comparator.reverseOrder()));

        return voList;
    }

    /**
     * 批量填充运动明细，仅向 AI 接口暴露运动名称和次数。
     */
    private void fillExerciseDetails(long userId, List<TimeRecordEntity> records,
                                     List<TimeRecordDateRangeVO> voList) {
        Set<Long> timeIds = records.stream()
                .map(TimeRecordEntity::getId)
                .filter(Objects::nonNull)
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

        secondaryLockGuard.checkMenus(userId, "/record/exercise");
        Map<Long, String> exerciseNameMap = userDictDataService
                .listUserVisibleDictData(userId, DictTypeEnum.EXERCISE_TYPE.getValue(), true)
                .stream()
                .filter(dict -> dict.getId() != null && dict.getDictLabel() != null)
                .collect(Collectors.toMap(
                        UserDictDataEntity::getId,
                        UserDictDataEntity::getDictLabel,
                        (existing, replacement) -> replacement));

        Map<Long, List<TimeRecordExerciseVO>> exercisesByTimeId = exerciseRecords.stream()
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

    @Override
    public TimeRecordVO getDetail(long userId, Long id) {
        LambdaQueryWrapper<TimeRecordEntity> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(TimeRecordEntity::getId, id);
        lambdaQueryWrapper.eq(TimeRecordEntity::getUserId, userId);
        TimeRecordEntity entity = this.getOne(lambdaQueryWrapper);
        if (entity == null) {
            return null;
        }

        TimeRecordVO vo = new TimeRecordVO();
        BeanUtil.copyProperties(entity, vo);

        List<ExerciseRecordEntity> exercises = exerciseRecordService.lambdaQuery()
                .eq(ExerciseRecordEntity::getTimeId, id)
                .eq(ExerciseRecordEntity::getUserId, userId)
                .list();
        if (!exercises.isEmpty()) secondaryLockGuard.checkMenus(userId, "/record/exercise");
        vo.setExercises(RecordApiConvertor.INSTANCE.toExerciseRecordVOList(exercises));

        return vo;
    }

    @Override
    public Integer findLatestEndMinute(long userId, LocalDate date) {
        TimeRecordEntity lastRecord = this.lambdaQuery()
                .select(TimeRecordEntity::getEndMinute)
                .eq(TimeRecordEntity::getUserId, userId)
                .eq(TimeRecordEntity::getDate, date)
                .orderByDesc(TimeRecordEntity::getEndMinute)
                .last("limit 1")
                .one();
        return lastRecord == null ? null : lastRecord.getEndMinute();
    }

    /** 日期修改需要清理新旧两天；直接访问缓存，避免同类调用使注解失效。 */
    private void invalidateDayCache(long userId, LocalDate... dates) {
        Runnable invalidate = () -> {
            for (LocalDate date : Arrays.stream(dates).filter(Objects::nonNull).distinct().toList()) {
                try {
                    var cache = timeRecordCacheManager.getCache("timeRecordDay:v1");
                    if (cache != null) cache.evict(userId + ":" + date);
                } catch (RuntimeException exception) {
                    log.warn("Time record cache eviction failed: userId={}, date={}", userId, date, exception);
                }
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() { invalidate.run(); }
                    });
        } else {
            invalidate.run();
        }
    }

    private void checkExistingExercises(long userId, List<Long> timeIds) {
        if (!timeIds.isEmpty() && exerciseRecordService.count(new LambdaQueryWrapper<ExerciseRecordEntity>()
                .eq(ExerciseRecordEntity::getUserId, userId).in(ExerciseRecordEntity::getTimeId, timeIds)) > 0) {
            secondaryLockGuard.checkMenus(userId, "/record/exercise");
        }
    }

    private void validateCategory(Long categoryId, long userId, boolean existingCategory) {
        if (categoryId == null) return;
        boolean allowed = timeTrackerCategoryService.count(new LambdaQueryWrapper<TimeTrackerCategoryEntity>()
                .eq(TimeTrackerCategoryEntity::getId, categoryId)
                .and(w -> w.eq(TimeTrackerCategoryEntity::getUserId, userId)
                        .or().eq(TimeTrackerCategoryEntity::getUserId, 0L))) > 0;
        if (!allowed) throw new IllegalArgumentException("分类不存在或无权使用");
        if (!existingCategory && timeTrackerCategoryService.listUserVisibleCategories(userId).stream()
                .noneMatch(c -> Objects.equals(c.getId(), categoryId))) {
            throw new IllegalArgumentException("分类或上级分类已停用，请重新选择");
        }
    }

    private void updateRelateStatusIfNecessary(TimeRecordEntity entity, long userId) {
        if (entity.getRelateId() != null && entity.getRelateType() != null) {
            log.info("Check relate status, type: {}, id: {}", entity.getRelateType(), entity.getRelateId());
            if (entity.getRelateType().equals(RelateTypeEnum.READ.getValue())) {
                secondaryLockGuard.checkMenus(userId, "/record/read");
                readRecordService.update(new LambdaUpdateWrapper<ReadRecordEntity>()
                        .eq(ReadRecordEntity::getId, entity.getRelateId())
                        .eq(ReadRecordEntity::getUserId, userId)
                        .eq(ReadRecordEntity::getStatus, ProgressStatusEnum.NOT_STARTED)
                        .set(ReadRecordEntity::getStatus, ProgressStatusEnum.IN_PROGRESS)
                        .set(ReadRecordEntity::getUpdateUser, userId)
                        .set(ReadRecordEntity::getUpdateTime, LocalDateTime.now()));
            } else if (entity.getRelateType().equals(RelateTypeEnum.MOVIE.getValue())) {
                secondaryLockGuard.checkMenus(userId, "/record/movie");
                movieService.update(new LambdaUpdateWrapper<MovieEntity>()
                        .eq(MovieEntity::getId, entity.getRelateId())
                        .eq(MovieEntity::getUserId, userId)
                        .eq(MovieEntity::getStatus, ProgressStatusEnum.NOT_STARTED)
                        .set(MovieEntity::getStatus, ProgressStatusEnum.IN_PROGRESS)
                        .set(MovieEntity::getUpdateUser, userId)
                        .set(MovieEntity::getUpdateTime, LocalDateTime.now()));
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String saveTimeRecord(long userId, TimeRecordReq timeRecordReq) {
        TimeRecordEntity entity = TimeRecordConvertor.INSTANCE.Req2Entity(timeRecordReq);
        // 新增时始终由服务端生成 ID。
        entity.setId(null);
        validateRelate(entity);
        if (entity.getDate() == null) entity.setDate(LocalDate.now());
        List<ExerciseRecordReq> exerciseRecordReqs = timeRecordReq.getExercises();

        validateCategory(entity.getCategoryId(), userId, false);
        entity.setUserId(userId);
        entity.setCreateUser(userId);
        entity.setUpdateUser(userId);
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        entity.setIsDeleted(0);

        calculateDuration(entity);

        if (!this.save(entity)) {
            throw new IllegalStateException("保存时间记录失败");
        }
        updateRelateStatusIfNecessary(entity, userId);

        if (exerciseRecordReqs != null && !exerciseRecordReqs.isEmpty()) {
            secondaryLockGuard.checkMenus(userId, "/record/exercise");
            List<ExerciseRecordEntity> validExercises = new ArrayList<>();
            for (ExerciseRecordReq exerciseReq : exerciseRecordReqs) {
                if (exerciseReq.getExerciseTypeId() == null) {
                    continue;
                }
                ExerciseRecordEntity exercise = new ExerciseRecordEntity();
                exercise.setUserId(userId);
                exercise.setTimeId(entity.getId());
                exercise.fillCreateCommonField(userId);
                exercise.setExerciseDate(entity.getDate());
                exercise.setExerciseTypeId(exerciseReq.getExerciseTypeId());
                exercise.setExerciseCount(exerciseReq.getExerciseCount());
                exercise.setDescription(exerciseReq.getDescription());
                validExercises.add(exercise);
            }
            if (!validExercises.isEmpty()) {
                exerciseRecordService.saveBatch(validExercises);
            }
        }
        invalidateDayCache(userId, entity.getDate());
        return IdCodec.encode(entity.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTimeRecord(long userId, TimeRecordReq timeRecordReq) {
        TimeRecordEntity entity = TimeRecordConvertor.INSTANCE.Req2Entity(timeRecordReq);
        entity.setId(IdCodec.decode(timeRecordReq.getId()));
        validateRelate(entity);
        List<ExerciseRecordReq> exerciseRecordReqs = timeRecordReq.getExercises();

        TimeRecordEntity existing = timeRecordMapper.lockOwned(entity.getId(), userId);
        if (existing == null) {
            throw new RuntimeException("记录不存在或无权限");
        }
        validateCategory(entity.getCategoryId(), userId, Objects.equals(entity.getCategoryId(), existing.getCategoryId()));
        // 防止请求方篡改记录归属
        entity.setUserId(null);
        entity.setUpdateUser(userId);
        entity.setUpdateTime(LocalDateTime.now());

        // 起止时间和时长作为一个整体保存；未提交的端点取锁定后的当前值。
        if (entity.getStartMinute() == null) entity.setStartMinute(existing.getStartMinute());
        if (entity.getEndMinute() == null) entity.setEndMinute(existing.getEndMinute());
        calculateDuration(entity);

        if (!this.updateById(entity)) throw new IllegalStateException("更新时间记录失败");
        updateRelateStatusIfNecessary(entity, userId);

        syncExercises(userId, entity.getId(),
                entity.getDate() == null ? existing.getDate() : entity.getDate(), exerciseRecordReqs);
        invalidateDayCache(userId, existing.getDate(), entity.getDate());
    }

    /** 关联类型和关联业务 ID 必须成对提交，单独出现任何一个都会写成无法解释的半关联。 */
    private void validateRelate(TimeRecordEntity entity) {
        if ((entity.getRelateType() == null) != (entity.getRelateId() == null)) {
            throw new IllegalArgumentException("关联业务类型和关联业务ID必须同时提交");
        }
    }

    /** 分钟采用闭区间；时长只由有效起止时间计算，不能由请求独立写入。 */
    private void calculateDuration(TimeRecordEntity entity) {
        Integer start = entity.getStartMinute();
        Integer end = entity.getEndMinute();
        if (start == null || end == null) {
            throw new IllegalArgumentException("开始时间和结束时间不能为空");
        }
        if (start < 0 || start > 1439 || end < 0 || end > 1439) {
            throw new IllegalArgumentException("开始时间和结束时间必须在 0 到 1439 之间");
        }
        if (end < start) {
            throw new IllegalArgumentException("结束时间不能早于开始时间");
        }
        entity.setDurationMinutes(end - start + 1);
    }

    /** 按完整明细列表同步：已有 ID 原位更新，无 ID 新增，未提交的旧明细逻辑删除。 */
    private void syncExercises(long userId, Long timeId, LocalDate date, List<ExerciseRecordReq> requests) {
        Map<Long, ExerciseRecordEntity> remaining = exerciseRecordService.list(
                        new LambdaQueryWrapper<ExerciseRecordEntity>()
                                .eq(ExerciseRecordEntity::getTimeId, timeId)
                                .eq(ExerciseRecordEntity::getUserId, userId))
                .stream().collect(Collectors.toMap(ExerciseRecordEntity::getId, exercise -> exercise));
        List<ExerciseRecordReq> exercises = requests == null ? List.of() : requests;
        if (!remaining.isEmpty() || !exercises.isEmpty()) {
            secondaryLockGuard.checkMenus(userId, "/record/exercise");
        }

        Set<Long> submittedIds = new HashSet<>();
        for (ExerciseRecordReq request : exercises) {
            if (request.getId() == null) continue;
            Long id = parseExerciseId(request.getId());
            if (!remaining.containsKey(id)) {
                throw new IllegalArgumentException("运动记录不存在或不属于当前时迹");
            }
            if (!submittedIds.add(id)) {
                throw new IllegalArgumentException("运动记录ID不能重复");
            }
        }

        List<ExerciseRecordEntity> additions = new ArrayList<>();
        for (ExerciseRecordReq request : exercises) {
            if (request.getExerciseTypeId() == null) continue;
            if (request.getId() == null) {
                ExerciseRecordEntity exercise = new ExerciseRecordEntity();
                exercise.setUserId(userId);
                exercise.setTimeId(timeId);
                exercise.fillCreateCommonField(userId);
                exercise.setExerciseDate(date);
                exercise.setExerciseTypeId(request.getExerciseTypeId());
                exercise.setExerciseCount(request.getExerciseCount());
                exercise.setDescription(request.getDescription());
                additions.add(exercise);
                continue;
            }

            ExerciseRecordEntity existing = remaining.remove(parseExerciseId(request.getId()));
            if (Objects.equals(existing.getExerciseDate(), date)
                    && Objects.equals(existing.getExerciseTypeId(), request.getExerciseTypeId())
                    && Objects.equals(existing.getExerciseCount(), request.getExerciseCount())
                    && Objects.equals(existing.getDescription(), request.getDescription())) {
                continue;
            }
            // 显式 set 保留次数、描述清空为 null 的语义，不覆盖创建审计信息。
            boolean updated = exerciseRecordService.update(new LambdaUpdateWrapper<ExerciseRecordEntity>()
                    .eq(ExerciseRecordEntity::getId, existing.getId())
                    .eq(ExerciseRecordEntity::getTimeId, timeId)
                    .eq(ExerciseRecordEntity::getUserId, userId)
                    .set(ExerciseRecordEntity::getExerciseDate, date)
                    .set(ExerciseRecordEntity::getExerciseTypeId, request.getExerciseTypeId())
                    .set(ExerciseRecordEntity::getExerciseCount, request.getExerciseCount())
                    .set(ExerciseRecordEntity::getDescription, request.getDescription())
                    .set(ExerciseRecordEntity::getUpdateUser, userId)
                    .set(ExerciseRecordEntity::getUpdateTime, LocalDateTime.now()));
            if (!updated) throw new IllegalStateException("更新运动记录失败");
        }
        if (!remaining.isEmpty() && !exerciseRecordService.remove(new LambdaQueryWrapper<ExerciseRecordEntity>()
                .eq(ExerciseRecordEntity::getTimeId, timeId)
                .eq(ExerciseRecordEntity::getUserId, userId)
                .in(ExerciseRecordEntity::getId, remaining.keySet()))) {
            throw new IllegalStateException("删除运动记录失败");
        }
        if (!additions.isEmpty() && !exerciseRecordService.saveBatch(additions)) {
            throw new IllegalStateException("新增运动记录失败");
        }
    }

    private Long parseExerciseId(String id) {
        try {
            return Long.valueOf(id);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("运动记录ID格式不正确");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeById(Long id, long userId) {
        TimeRecordEntity existing = this.getOne(new LambdaQueryWrapper<TimeRecordEntity>()
                .eq(TimeRecordEntity::getId, id).eq(TimeRecordEntity::getUserId, userId));
        if (existing == null) return;
        checkExistingExercises(userId, List.of(id));
        LambdaQueryWrapper<TimeRecordEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(TimeRecordEntity::getId, id);
        queryWrapper.eq(TimeRecordEntity::getUserId, userId);
        this.remove(queryWrapper);

        // 删除关联的运动记录
        exerciseRecordService.remove(new LambdaQueryWrapper<ExerciseRecordEntity>()
                .eq(ExerciseRecordEntity::getTimeId, id)
                .eq(ExerciseRecordEntity::getUserId, userId));
        invalidateDayCache(userId, existing.getDate());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeByDate(LocalDate date, long userId) {
        // 1. 查询该日期下的所有时间记录ID
        List<TimeRecordEntity> list = this.lambdaQuery()
                .select(TimeRecordEntity::getId)
                .eq(TimeRecordEntity::getDate, date)
                .eq(TimeRecordEntity::getUserId, userId)
                .list();

        if (list.isEmpty()) {
            return;
        }

        List<Long> ids = list.stream().map(TimeRecordEntity::getId).toList();

        checkExistingExercises(userId, ids);
        // 2. 删除关联的运动记录
        exerciseRecordService.remove(new LambdaQueryWrapper<ExerciseRecordEntity>()
                .in(ExerciseRecordEntity::getTimeId, ids)
                .eq(ExerciseRecordEntity::getUserId, userId));

        // 3. 删除时间记录
        this.removeByIds(ids);
        invalidateDayCache(userId, date);
    }

    @Override
    public Long recommendType(long userId, String date, int time) {
        // time 表示当天的第几分钟：0 为 00:00，1439 为 23:59。
        if (time < 0 || time > 1439) throw new IllegalArgumentException("时间必须在 0 到 1439 之间");
        LocalDate localDate = LocalDate.parse(date);
        // 根据工作日历识别工作日/非工作日，包含法定节假日和调休补班，不直接按星期推断。
        Boolean isWorkday = recommendationDataCache.get("workday",
                () -> workCalendarService.isWorkday(localDate), localDate);
        if (isWorkday == null) {
            // 目标日期的日历缺失时直接结束推荐，由 Controller 将 null 转成空字符串。
            log.info("Time category recommendation completed: userId={}, date={}, minute={}, "
                    + "source=NONE, reason=WORK_CALENDAR_MISSING, categoryId=null", userId, date, time);
            return null;
        }
        // targetDate 在这里是参考日：严格早于 localDate 的最近一个同类日；日历不完整时可能为 null。
        LocalDate targetDate = recommendationDataCache.get("comparableDate",
                () -> workCalendarService.findPreviousComparableDate(localDate), localDate);

        // 尝试参考日同一时刻的记录；没有唯一有效记录则返回 null。
        return recommendFromReferenceDay(userId, localDate, time, targetDate, LocalDateTime.now());
    }

    /**
     * 使用最近一个同类日的记录兜底，只接受覆盖目标分钟且分类仍对用户可见的唯一记录。
     *
     * <p>参考日必须早于目标日期，且不能晚于当前日期；若参考日就是今天，
     * 仅使用结束时间严格早于当前分钟的记录。无匹配记录或存在多条候选记录时均返回 null。</p>
     *
     * @param userId 用户 ID
     * @param date 本次需要推荐分类的目标日期
     * @param minute 目标时刻距当天 00:00 的分钟数
     * @param reference 最近一个同类日的日期，可为 null
     * @param now 当前时间，用于排除尚未结束的参考记录
     * @return 唯一有效参考记录的分类 ID；无法确定时返回 null
     */
    Long recommendFromReferenceDay(long userId, LocalDate date, int minute,
                                  LocalDate reference, LocalDateTime now) {
        if (reference == null || !reference.isBefore(date) || reference.isAfter(now.toLocalDate())) {
            return noReferenceRecommendation(userId, date, minute, "NO_PAST_REFERENCE_DAY");
        }
        int endBefore = reference.equals(now.toLocalDate()) ? now.getHour() * 60 + now.getMinute() : 1440;
        if (minute >= endBefore) {
            return noReferenceRecommendation(userId, date, minute, "REFERENCE_TIME_NOT_PAST");
        }
        List<TimeTrackerCategoryEntity> categories = recommendationDataCache.get("visibleCategories",
                () -> timeTrackerCategoryService.listUserVisibleCategories(userId), userId);
        var visibleIds = categories.stream().filter(c -> c.getId() != null && c.getName() != null)
                .map(TimeTrackerCategoryEntity::getId).collect(Collectors.toSet());
        if (visibleIds.isEmpty()) return noReferenceRecommendation(userId, date, minute, "NO_VISIBLE_CATEGORIES");

        List<TimeRecordEntity> records = recommendationDataCache.get("referenceRecords",
                () -> timeRecordMapper.findReferenceRecords(userId, reference.toString(), minute, endBefore),
                userId, reference, minute, endBefore);
        var candidates = records.stream().filter(record -> visibleIds.contains(record.getCategoryId())).toList();
        if (candidates.size() != 1) {
            return noReferenceRecommendation(userId, date, minute,
                    candidates.isEmpty() ? "NO_VALID_REFERENCE_RECORD" : "OVERLAPPING_REFERENCE_RECORDS");
        }
        return logRecommendation(userId, date.toString(), minute, "REFERENCE_DAY", candidates.getFirst().getCategoryId());
    }

    private Long noReferenceRecommendation(long userId, LocalDate date, int minute, String reason) {
        log.info("Time category recommendation completed: userId={}, date={}, minute={}, "
                + "source=NONE, reason={}, categoryId=null", userId, date, minute, reason);
        return null;
    }

    private Long logRecommendation(long userId, String date, int minute, String source, Long categoryId) {
        log.info("Time category recommendation completed: userId={}, date={}, minute={}, source={}, categoryId={}",
                userId, date, minute, source, categoryId);
        return categoryId;
    }

    @Override
    public RecommendNextVO recommendNext(long userId, String date) {
        LocalDate targetDate = LocalDate.parse(date);
        LambdaQueryWrapper<TimeRecordEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.select(TimeRecordEntity::getStartMinute, TimeRecordEntity::getEndMinute, TimeRecordEntity::getCategoryId)
                .eq(TimeRecordEntity::getUserId, userId)
                .eq(TimeRecordEntity::getDate, targetDate);
        List<TimeRecordEntity> records = new ArrayList<>(recommendationDataCache.get("nextRecords",
                () -> this.list(queryWrapper), userId, targetDate));
        
        TimeRecordEntity recommend = calculateRecommendNext(records, targetDate);
        return RecommendNextVO.builder()
                .recommend(recommend)
                .records(records)
                .build();
    }

    @Override
    public RecommendNextVO recommendNextWithCategory(long userId, String date) {
        RecommendNextVO result = recommendNext(userId, date);

        TimeRecordEntity recommend = result.getRecommend();
        if (recommend == null) {
            log.info("Time next recommendation skipped: userId={}, date={}, reason=NO_REMAINING_TIME", userId, date);
            return result;
        }

        Long categoryId = recommendType(userId, date, recommend.getStartMinute());
        recommend.setCategoryId(categoryId);
        return result;
    }

    @Override
    public List<Map<String, Object>> listRelateTypes() {
        return RelateTypeEnum.toList();
    }

    /**
     * 计算推荐的下一个时间块
     * @return 推荐时间块；当天没有剩余分钟时返回 null
     */
    public TimeRecordEntity calculateRecommendNext(List<TimeRecordEntity> records, LocalDate targetDate) {
        records.sort(Comparator.comparingInt(TimeRecordEntity::getStartMinute));

        boolean isToday = LocalDate.now().equals(targetDate);
        int lastEndTime = -1;
        int startTime = 0;
        int endTime = 0;
        boolean foundGap = false;

        for (TimeRecordEntity record : records) {
            if (record.getStartMinute() > lastEndTime + 1) {
                startTime = lastEndTime + 1;
                endTime = record.getStartMinute() - 1;
                foundGap = true;
                break;
            }
            lastEndTime = Math.max(lastEndTime, record.getEndMinute());
        }

        if (!foundGap) {
            startTime = lastEndTime + 1;
            if (startTime > 1439) {
                return null;
            }
            if (isToday) {
                LocalDateTime now = LocalDateTime.now();
                endTime = now.getHour() * 60 + now.getMinute();
            } else {
                endTime = startTime + 29;
            }
        }

        if (endTime < startTime) {
            endTime = startTime;
        }

        // 限制最大值为 1439 (23:59)
        if (endTime > 1439) {
            endTime = 1439;
        }

        TimeRecordEntity result = new TimeRecordEntity();
        result.setStartMinute(startTime);
        result.setEndMinute(endTime);
        result.setDate(targetDate);
        result.setDurationMinutes(endTime - startTime + 1);

        return result;
    }
}
