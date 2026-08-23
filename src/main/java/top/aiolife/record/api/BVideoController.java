package top.aiolife.record.api;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import top.aiolife.core.constant.StatusConst;
import top.aiolife.core.constant.ResponseCodeConst;
import top.aiolife.core.query.CommonQuery;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.resq.PageResp;
import top.aiolife.core.util.SysUtil;
import top.aiolife.record.enums.StudyEnum;
import top.aiolife.record.mapper.IBVideoMapper;
import top.aiolife.record.pojo.entity.BVideoEntity;
import top.aiolife.record.pojo.vo.BVideoStatisticsVO;
import top.aiolife.record.pojo.vo.StatusCount;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 类功能描述
 *
 * @author Lys
 * @date 2025/10/06 23:13
 */
@Slf4j
@RestController
@AllArgsConstructor
@RequestMapping("/b-video")
public class BVideoController {

    private IBVideoMapper bVideoMapper;

    public IBVideoMapper getBaseMapper() {
        return bVideoMapper;
    }

    @PostMapping("/query")
    public ApiResponse<PageResp<BVideoEntity>> query(
            @RequestBody CommonQuery<BVideoEntity> query) {
        long userId = StpUtil.getLoginIdAsLong();
        LambdaQueryWrapper<BVideoEntity> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(BVideoEntity::getUserId, userId);
        lambdaQueryWrapper.eq(BVideoEntity::getIsDeleted, StatusConst.NO_DELETE);
        BVideoEntity condition = query.getCondition();
        if (SysUtil.isNotEmpty(condition.getStatus())) {
            // 0 为查询全部状态
            if (0 != condition.getStatus()) {
                lambdaQueryWrapper.eq(BVideoEntity::getStatus,
                        condition.getStatus());
            }
        }

        lambdaQueryWrapper.orderByAsc(BVideoEntity::getStatus);
        lambdaQueryWrapper.orderByDesc(BVideoEntity::getUpdateTime);
        Page<BVideoEntity> page = new Page<>(query.getPage(), query.getPageSize());
        IPage<BVideoEntity> iPage = bVideoMapper.selectPage(page, lambdaQueryWrapper);
        PageResp<BVideoEntity> objectPageResp = PageResp.of(iPage.getRecords(), iPage.getTotal());
        return ApiResponse.success(objectPageResp);
    }

    @PostMapping
    public ApiResponse<Boolean> insert(@RequestBody BVideoEntity entity) {
        long userId = StpUtil.getLoginIdAsLong();

        LambdaQueryWrapper<BVideoEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(BVideoEntity::getBvid, entity.getBvid());
        queryWrapper.eq(BVideoEntity::getUserId, userId);
        Long count = getBaseMapper().selectCount(queryWrapper);
        if (count > 0) {
            return ApiResponse.error(ResponseCodeConst.RSCODE_COMMON_FAIL, "该视频已存在，无法重复添加");
        }

        entity.setUserId(userId);
        entity.setUpdateUser(userId);
        entity.setUpdateTime(LocalDateTime.now());
        if (entity.getStatus() == null) {
            entity.setStatus(StudyEnum.IN_PROGRESS.getValue());
        }
        if (entity.getStatus() == StudyEnum.COMPLETED.getValue()) {
            entity.setWatchedDuration(entity.getDuration());
        }
        boolean b = getBaseMapper().insert(entity) > 0;
        return ApiResponse.success(b);
    }

    @PutMapping("/{id}")
    public ApiResponse<Boolean> update(@PathVariable Long id, @RequestBody BVideoEntity entity) {
        long userId = StpUtil.getLoginIdAsLong();

        LambdaQueryWrapper<BVideoEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BVideoEntity::getId, id);
        wrapper.eq(BVideoEntity::getUserId, userId);
        
        BVideoEntity existEntity = getBaseMapper().selectOne(wrapper);
        if (existEntity == null) {
            return ApiResponse.error(ResponseCodeConst.RSCODE_COMMON_FAIL, "无权限更新该数据或数据不存在");
        }

        entity.setId(id);
        entity.setUserId(userId);
        entity.setUpdateUser(userId);
        entity.setUpdateTime(LocalDateTime.now());
        if (entity.getStatus() != null && entity.getStatus() == StudyEnum.COMPLETED.getValue()) {
            entity.setWatchedDuration(entity.getDuration());
        }
        
        boolean b = getBaseMapper().update(entity, wrapper) > 0;
        return ApiResponse.success(b);
    }


    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(@PathVariable Long id) {
        long userId = StpUtil.getLoginIdAsLong();
        
        LambdaQueryWrapper<BVideoEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BVideoEntity::getId, id);
        wrapper.eq(BVideoEntity::getUserId, userId);

        boolean b = getBaseMapper().delete(wrapper) > 0;
        if (!b) {
            return ApiResponse.error(ResponseCodeConst.RSCODE_COMMON_FAIL, "无权限删除该数据或数据不存在");
        }
        return ApiResponse.success(b);
    }

    @GetMapping("/getStatusCount")
    public ApiResponse<Map> getStatusCount() {
        long userId = StpUtil.getLoginIdAsLong();
        List<StatusCount> statusCount = getBaseMapper().getStatusCount(userId);
        Map<Integer, Integer> map = statusCount.stream().collect(
                Collectors.toMap(StatusCount::getStatus, StatusCount::getCount));
        return ApiResponse.success(map);
    }

    @GetMapping("/statistics")
    public ApiResponse<BVideoStatisticsVO> statistics() {
        long userId = StpUtil.getLoginIdAsLong();
        BVideoStatisticsVO statisticsVO = new BVideoStatisticsVO();
        Integer watchTime = bVideoMapper.getWatchTime(userId);
        Integer totalTime = bVideoMapper.getTotalTime(userId);
        if (watchTime != null) {
            statisticsVO.setStudiedSeconds(watchTime);
        }
        if (totalTime != null) {
            statisticsVO.setTotalSeconds(totalTime);
        }
        int totalTimeValue = (totalTime != null) ? totalTime : 0;
        int watchedTimeValue = (watchTime != null) ? watchTime : 0;
        statisticsVO.setUnstudiedSeconds(totalTimeValue - watchedTimeValue);

        return ApiResponse.success(statisticsVO);
    }

    @PostMapping("/tagVideo")
    public ApiResponse<Boolean> tagVideo(@RequestBody BVideoEntity entity) {
        long userId = StpUtil.getLoginIdAsLong();
        entity.setUserId(userId);

        LambdaQueryWrapper<BVideoEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(BVideoEntity::getBvid, entity.getBvid());
        queryWrapper.eq(BVideoEntity::getUserId, userId);
        if (getBaseMapper().selectCount(queryWrapper) > 0) {
            return ApiResponse.error(ResponseCodeConst.RSCODE_COMMON_FAIL, "该视频已存在，无法重复添加");
        }

        entity.setCreateUser(userId);
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateUser(userId);
        entity.setUpdateTime(LocalDateTime.now());
        if (entity.getStatus() == null) {
            entity.setStatus(StudyEnum.IN_PROGRESS.getValue());
        }
        getBaseMapper().insert(entity);
        return ApiResponse.success();
    }

    @PostMapping("/syncProgress")
    public ApiResponse<Boolean> syncProgress(@RequestBody BVideoEntity entity) {
        long userId = StpUtil.getLoginIdAsLong();
        entity.setUserId(userId);
        log.info("bvid: {}, currentEpisode: {}, watchedDuration: {}", entity.getBvid(),
                entity.getCurrentEpisode(), entity.getWatchedDuration());

        LambdaQueryWrapper<BVideoEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(BVideoEntity::getUserId, userId);
        queryWrapper.eq(BVideoEntity::getBvid, entity.getBvid());
        BVideoEntity exist = getBaseMapper().selectOne(queryWrapper);

        if (exist != null) {
            LambdaUpdateWrapper<BVideoEntity> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.set(BVideoEntity::getCurrentEpisode, entity.getCurrentEpisode());
            updateWrapper.set(BVideoEntity::getWatchedDuration, entity.getWatchedDuration());
            updateWrapper.set(BVideoEntity::getLastWatched, LocalDateTime.now());
            updateWrapper.set(BVideoEntity::getUpdateTime, LocalDateTime.now());
            updateWrapper.set(BVideoEntity::getUpdateUser, userId);
            updateWrapper.eq(BVideoEntity::getId, exist.getId());
            getBaseMapper().update(null, updateWrapper);
            log.info("syncProgress updated, id: {}", exist.getId());
        } else {
            entity.setCreateUser(userId);
            entity.setCreateTime(LocalDateTime.now());
            entity.setUpdateUser(userId);
            entity.setUpdateTime(LocalDateTime.now());
            entity.setLastWatched(LocalDateTime.now());
            if (entity.getStatus() == null) {
                entity.setStatus(StudyEnum.IN_PROGRESS.getValue());
            }
            getBaseMapper().insert(entity);
            log.info("syncProgress inserted, bvid: {}", entity.getBvid());
        }
        return ApiResponse.success();
    }
}