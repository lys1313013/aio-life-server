package top.aiolife.record.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import top.aiolife.core.util.SysUtil;
import top.aiolife.record.convertor.TimeRecordConvertor;
import top.aiolife.record.prediction.JevCategoryRecommendationService;
import top.aiolife.record.prediction.RecommendationDataCache;
import top.aiolife.record.mapper.ITimeRecordMapper;
import top.aiolife.record.pojo.entity.ExerciseRecordEntity;
import top.aiolife.record.pojo.entity.TimeRecordEntity;
import top.aiolife.record.pojo.req.ExerciseRecordReq;
import top.aiolife.record.pojo.req.TimeRecordReq;
import top.aiolife.record.pojo.vo.RecommendNextVO;
import top.aiolife.record.service.IExerciseRecordService;
import top.aiolife.record.service.IReadRecordService;
import top.aiolife.record.service.IMovieService;
import top.aiolife.record.pojo.entity.ReadRecordEntity;
import top.aiolife.record.pojo.entity.MovieEntity;
import top.aiolife.record.pojo.enums.ProgressStatusEnum;
import top.aiolife.record.pojo.enums.RelateTypeEnum;
import top.aiolife.record.service.ITimeRecordService;
import top.aiolife.record.service.ITimeTrackerCategoryService;
import top.aiolife.record.pojo.entity.TimeTrackerCategoryEntity;
import top.aiolife.system.service.IWorkCalendarService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * 时间记录Service实现类
 *
 * @author Lys
 * @date 2026-01-10 23:55
 */
@Slf4j
@Service
@AllArgsConstructor
public class TimeRecordServiceImpl extends ServiceImpl<ITimeRecordMapper, TimeRecordEntity> implements ITimeRecordService {

    private final ITimeRecordMapper timeRecordMapper;
    private final IExerciseRecordService exerciseRecordService;
    private final IReadRecordService readRecordService;
    private final IMovieService movieService;
    private final IWorkCalendarService workCalendarService;
    private final JevCategoryRecommendationService jevRecommendationService;
    private final RecommendationDataCache recommendationDataCache;
    private final ITimeTrackerCategoryService timeTrackerCategoryService;

    private void updateRelateStatusIfNecessary(TimeRecordEntity entity, long userId) {
        if (entity.getRelateId() != null && entity.getRelateType() != null) {
            log.info("Check relate status, type: {}, id: {}", entity.getRelateType(), entity.getRelateId());
            if (entity.getRelateType().equals(RelateTypeEnum.READ.getValue())) {
                readRecordService.update(new LambdaUpdateWrapper<ReadRecordEntity>()
                        .eq(ReadRecordEntity::getId, entity.getRelateId())
                        .eq(ReadRecordEntity::getUserId, userId)
                        .eq(ReadRecordEntity::getStatus, ProgressStatusEnum.NOT_STARTED)
                        .set(ReadRecordEntity::getStatus, ProgressStatusEnum.IN_PROGRESS));
            } else if (entity.getRelateType().equals(RelateTypeEnum.MOVIE.getValue())) {
                movieService.update(new LambdaUpdateWrapper<MovieEntity>()
                        .eq(MovieEntity::getId, entity.getRelateId())
                        .eq(MovieEntity::getUserId, userId)
                        .eq(MovieEntity::getStatus, ProgressStatusEnum.NOT_STARTED)
                        .set(MovieEntity::getStatus, ProgressStatusEnum.IN_PROGRESS));
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String saveTimeRecord(TimeRecordReq timeRecordReq) {
        TimeRecordEntity entity = TimeRecordConvertor.INSTANCE.Req2Entity(timeRecordReq);
        // 新增时始终由服务端生成 ID，兼容旧客户端携带的临时 ID。
        entity.setId(null);
        List<ExerciseRecordReq> exerciseRecordReqs = timeRecordReq.getExercises();

        long userId = StpUtil.getLoginIdAsLong();
        entity.setUserId(userId);
        entity.setCreateUser(userId);
        entity.setUpdateUser(userId);
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        entity.setIsDeleted(0);

        // 限制时间最大值为 1439 (23:59)
        if (entity.getStartTime() != null && entity.getStartTime() > 1439) entity.setStartTime(1439);
        if (entity.getEndTime() != null && entity.getEndTime() > 1439) entity.setEndTime(1439);

        if (entity.getStartTime() != null && entity.getEndTime() != null) {
            entity.setDuration(entity.getEndTime() - entity.getStartTime() + 1);
        }

        if (!this.save(entity)) {
            throw new IllegalStateException("保存时间记录失败");
        }
        updateRelateStatusIfNecessary(entity, userId);

        if (exerciseRecordReqs != null && !exerciseRecordReqs.isEmpty()) {
            List<ExerciseRecordEntity> validExercises = new java.util.ArrayList<>();
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
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTimeRecord(TimeRecordReq timeRecordReq) {
        TimeRecordEntity entity = TimeRecordConvertor.INSTANCE.Req2Entity(timeRecordReq);
        List<ExerciseRecordReq> exerciseRecordReqs = timeRecordReq.getExercises();

        long userId = StpUtil.getLoginIdAsLong();
        TimeRecordEntity existing = this.getById(entity.getId());
        if (existing == null || existing.getUserId() == null || existing.getUserId() != userId) {
            throw new RuntimeException("记录不存在或无权限");
        }
        // 防止请求方篡改记录归属
        entity.setUserId(null);
        entity.setUpdateUser(userId);
        entity.setUpdateTime(LocalDateTime.now());

        // 限制时间最大值为 1439 (23:59)
        if (entity.getStartTime() != null && entity.getStartTime() > 1439) entity.setStartTime(1439);
        if (entity.getEndTime() != null && entity.getEndTime() > 1439) entity.setEndTime(1439);

        if (entity.getStartTime() != null && entity.getEndTime() != null) {
            entity.setDuration(entity.getEndTime() - entity.getStartTime() + 1);
        }

        this.updateById(entity);
        updateRelateStatusIfNecessary(entity, userId);

        // 删除旧的运动记录
        exerciseRecordService.remove(new LambdaQueryWrapper<ExerciseRecordEntity>()
                .eq(ExerciseRecordEntity::getTimeId, entity.getId())
                .eq(ExerciseRecordEntity::getUserId, userId));

        // 添加新的运动记录
        if (exerciseRecordReqs != null && !exerciseRecordReqs.isEmpty()) {
            List<ExerciseRecordEntity> validExercises = new java.util.ArrayList<>();
            for (ExerciseRecordReq exerciseReq : exerciseRecordReqs) {
                if (exerciseReq.getExerciseTypeId() == null) {
                    continue;
                }
                ExerciseRecordEntity exercise = new ExerciseRecordEntity();
                exercise.setUserId(userId);
                exercise.setTimeId(entity.getId());
                exercise.fillCreateCommonField(userId);
                exercise.setId(null);
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
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeById(String id, long userId) {
        LambdaQueryWrapper<TimeRecordEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(TimeRecordEntity::getId, id);
        queryWrapper.eq(TimeRecordEntity::getUserId, userId);
        this.remove(queryWrapper);

        // 删除关联的运动记录
        exerciseRecordService.remove(new LambdaQueryWrapper<ExerciseRecordEntity>()
                .eq(ExerciseRecordEntity::getTimeId, id)
                .eq(ExerciseRecordEntity::getUserId, userId));
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

        List<String> ids = list.stream().map(TimeRecordEntity::getId).toList();

        // 2. 删除关联的运动记录
        exerciseRecordService.remove(new LambdaQueryWrapper<ExerciseRecordEntity>()
                .in(ExerciseRecordEntity::getTimeId, ids)
                .eq(ExerciseRecordEntity::getUserId, userId));

        // 3. 删除时间记录
        this.removeByIds(ids);
    }

    @Override
    public Long recommendType(long userId, String date, int time, Long previousCategoryId) {
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

        // 优先使用 Jev AI，结合用户可见分类、目标日该时刻之前及参考日的有效历史记录预测。
        // previousCategoryId 仅为兼容旧调用保留，不影响预测，允许连续记录使用同一分类。
        Long predicted = jevRecommendationService.recommend(userId, localDate, time, isWorkday, targetDate);
        if (predicted != null) return logRecommendation(userId, date, time, "JEV", predicted);

        // AI 未给出分类时，尝试参考日同一时刻的记录；没有唯一有效记录则返回 null。
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
                .map(TimeTrackerCategoryEntity::getId).collect(java.util.stream.Collectors.toSet());
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
        queryWrapper.select(TimeRecordEntity::getStartTime, TimeRecordEntity::getEndTime, TimeRecordEntity::getCategoryId)
                .eq(TimeRecordEntity::getUserId, userId)
                .eq(TimeRecordEntity::getDate, targetDate);
        List<TimeRecordEntity> records = new java.util.ArrayList<>(recommendationDataCache.get("nextRecords",
                () -> this.list(queryWrapper), userId, targetDate));
        
        TimeRecordEntity recommend = calculateRecommendNext(records, targetDate);
        return RecommendNextVO.builder()
                .recommend(recommend)
                .records(records)
                .build();
    }

    /**
     * 计算推荐的下一个时间块
     * @return 推荐时间块；当天没有剩余分钟时返回 null
     */
    public TimeRecordEntity calculateRecommendNext(List<TimeRecordEntity> records, LocalDate targetDate) {
        records.sort(Comparator.comparingInt(TimeRecordEntity::getStartTime));

        boolean isToday = LocalDate.now().equals(targetDate);
        int lastEndTime = -1;
        int startTime = 0;
        int endTime = 0;
        boolean foundGap = false;

        for (TimeRecordEntity record : records) {
            if (record.getStartTime() > lastEndTime + 1) {
                startTime = lastEndTime + 1;
                endTime = record.getStartTime() - 1;
                foundGap = true;
                break;
            }
            lastEndTime = Math.max(lastEndTime, record.getEndTime());
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
        result.setStartTime(startTime);
        result.setEndTime(endTime);
        result.setDate(targetDate);
        result.setDuration(endTime - startTime + 1);

        return result;
    }
}
