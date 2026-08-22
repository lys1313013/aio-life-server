package top.aiolife.record.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import top.aiolife.core.util.SysUtil;
import top.aiolife.record.convertor.TimeRecordConvertor;
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

    private void updateRelateStatusIfNecessary(TimeRecordEntity entity) {
        if (entity.getRelateId() != null && entity.getRelateType() != null) {
            log.info("Check relate status, type: {}, id: {}", entity.getRelateType(), entity.getRelateId());
            if (entity.getRelateType().equals(RelateTypeEnum.READ.getValue())) {
                ReadRecordEntity readRecord = readRecordService.getById(entity.getRelateId());
                if (readRecord != null && readRecord.getStatus() != null && readRecord.getStatus().equals(ProgressStatusEnum.NOT_STARTED.getCode())) {
                    readRecord.setStatus(ProgressStatusEnum.IN_PROGRESS.getCode());
                    log.info("Updating read record {} status from NOT_STARTED to IN_PROGRESS", readRecord.getId());
                    readRecordService.updateById(readRecord);
                }
            } else if (entity.getRelateType().equals(RelateTypeEnum.MOVIE.getValue())) {
                MovieEntity movie = movieService.getById(entity.getRelateId());
                if (movie != null && movie.getStatus() != null && movie.getStatus().equals(ProgressStatusEnum.NOT_STARTED.getCode())) {
                    movie.setStatus(ProgressStatusEnum.IN_PROGRESS.getCode());
                    log.info("Updating movie {} status from NOT_STARTED to IN_PROGRESS", movie.getId());
                    movieService.updateById(movie);
                }
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveTimeRecord(TimeRecordReq timeRecordReq) {
        TimeRecordEntity entity = TimeRecordConvertor.INSTANCE.Req2Entity(timeRecordReq);
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

        this.save(entity);
        updateRelateStatusIfNecessary(entity);

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
        updateRelateStatusIfNecessary(entity);

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
        LocalDate localDate = LocalDate.parse(date);
        int dayOfWeek = localDate.getDayOfWeek().getValue();
        boolean isWorkday = dayOfWeek <= 5;

        // 0. 计算参考日期：周一参考上周五，周六参考上周日，其他参考前一天
        String targetDate;
        if (dayOfWeek == 6) {
            targetDate = localDate.minusDays(6).toString();
        } else if (dayOfWeek == 1) {
            targetDate = localDate.minusDays(3).toString();
        } else {
            targetDate = localDate.minusDays(1).toString();
        }

        // 1. 查参考日期同一时间段是否有记录
        TimeRecordEntity originalRecommend = this.baseMapper.recommendType(userId, targetDate, time);
        Long categoryId = originalRecommend != null ? originalRecommend.getCategoryId() : null;

        // 2. 如果 Step 1 未命中，降级到时间段历史高频（不排除任何分类）
        if (categoryId == null) {
            categoryId = this.baseMapper.getMostFrequentCategoryAtTime(userId, time, null, isWorkday);
        }

        // 3. 去重干预：如果推荐分类与上一条记录相同，尝试替换
        if (categoryId != null && categoryId.equals(previousCategoryId)) {
            // 3.1 预测后续行为：历史上紧跟在 previousCategoryId 之后最常出现的分类
            Long nextCategory = this.baseMapper.getMostFrequentNextCategory(userId, previousCategoryId, isWorkday);
            if (nextCategory != null) {
                return nextCategory;
            }

            // 3.2 降级：该时间段历史最高频，排除 previousCategoryId
            Long fallbackCategory = this.baseMapper.getMostFrequentCategoryAtTime(userId, time, previousCategoryId, isWorkday);
            if (fallbackCategory != null) {
                return fallbackCategory;
            }

            // 3.3 实在没有其他分类可选，返回原推荐（允许连续相同）
            return categoryId;
        }

        return categoryId;
    }

    @Override
    public RecommendNextVO recommendNext(long userId, String date) {
        LocalDate targetDate = LocalDate.parse(date);
        LambdaQueryWrapper<TimeRecordEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.select(TimeRecordEntity::getStartTime, TimeRecordEntity::getEndTime)
                .eq(TimeRecordEntity::getUserId, userId)
                .eq(TimeRecordEntity::getDate, targetDate);
        List<TimeRecordEntity> records = this.list(queryWrapper);
        
        TimeRecordEntity recommend = calculateRecommendNext(records, targetDate);
        return RecommendNextVO.builder()
                .recommend(recommend)
                .records(records)
                .build();
    }

    /**
     * 计算推荐的下一个时间块
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
            if (isToday) {
                LocalDateTime now = LocalDateTime.now();
                endTime = now.getHour() * 60 + now.getMinute();
            } else {
                endTime = startTime + 30;
            }
        }

        if (endTime < startTime) {
            endTime = startTime;
        }

        // 限制最大值为 1439 (23:59)
        if (startTime > 1439) {
            startTime = 1439;
        }
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
