package top.aiolife.record.api;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import top.aiolife.core.constant.ResponseCodeConst;
import top.aiolife.core.constant.StatusConst;
import top.aiolife.core.query.CommonQuery;
import top.aiolife.core.query.QueryParams;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.resq.PageResp;
import top.aiolife.record.convertor.RecordApiConvertor;
import top.aiolife.record.mapper.IBVideoMapper;
import top.aiolife.record.pojo.entity.BVideoEntity;
import top.aiolife.record.pojo.enums.ProgressStatusEnum;
import top.aiolife.record.pojo.query.BVideoQuery;
import top.aiolife.record.pojo.req.BVideoCreateReq;
import top.aiolife.record.pojo.req.BVideoProgressReq;
import top.aiolife.record.pojo.req.BVideoUpdateReq;
import top.aiolife.record.pojo.vo.BVideoCoverVO;
import top.aiolife.record.pojo.vo.BVideoStatisticsVO;
import top.aiolife.record.pojo.vo.BVideoVO;
import top.aiolife.record.pojo.vo.StatusCount;
import top.aiolife.record.service.VideoCoverService;

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

    private static final List<String> STATUS_ORDER_CODES = List.of(
            ProgressStatusEnum.NOT_STARTED.getCode(),
            ProgressStatusEnum.IN_PROGRESS.getCode(),
            ProgressStatusEnum.ON_HOLD.getCode(),
            ProgressStatusEnum.COMPLETED.getCode());

    private IBVideoMapper bVideoMapper;
    private VideoCoverService videoCovers;

    public IBVideoMapper getBaseMapper() {
        return bVideoMapper;
    }

    @GetMapping("/query")
    public ApiResponse<PageResp<BVideoVO>> query(
            @QueryParams CommonQuery<BVideoQuery> requestQuery) {
        CommonQuery<BVideoEntity> query = requestQuery.map(RecordApiConvertor.INSTANCE::fromBVideoQuery);
        long userId = StpUtil.getLoginIdAsLong();
        LambdaQueryWrapper<BVideoEntity> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(BVideoEntity::getUserId, userId);
        lambdaQueryWrapper.eq(BVideoEntity::getIsDeleted, StatusConst.NO_DELETE);
        BVideoEntity condition = query.getCondition();
        if (condition.getStatus() != null) {
            lambdaQueryWrapper.eq(BVideoEntity::getStatus, condition.getStatus());
        }

        Page<BVideoEntity> page = new Page<>(query.getPage(), query.getPageSize());
        IPage<BVideoEntity> iPage = bVideoMapper.selectPageWithStatusOrder(
                page, lambdaQueryWrapper, STATUS_ORDER_CODES);
        PageResp<BVideoEntity> objectPageResp = PageResp.of(iPage.getRecords(), iPage.getTotal());
        return ApiResponse.success(RecordApiConvertor.INSTANCE.toBVideoVOPage(objectPageResp));
    }

    @PostMapping
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Boolean> insert(@Valid @RequestBody BVideoCreateReq entityReq) {
        BVideoEntity entity = RecordApiConvertor.INSTANCE.fromBVideoCreateReq(entityReq);
        long userId = StpUtil.getLoginIdAsLong();

        LambdaQueryWrapper<BVideoEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(BVideoEntity::getBvid, entity.getBvid());
        queryWrapper.eq(BVideoEntity::getUserId, userId);
        Long count = getBaseMapper().selectCount(queryWrapper);
        if (count > 0) {
            return ApiResponse.error(ResponseCodeConst.COMMON_FAIL, "该视频已存在，无法重复添加");
        }

        entity.setUserId(userId);
        entity.fillCreateCommonField(userId);
        if (entity.getStatus() == null) {
            entity.setStatus(ProgressStatusEnum.IN_PROGRESS);
        }
        if (entity.getStatus() == ProgressStatusEnum.COMPLETED) {
            entity.setWatchedDuration(entity.getDuration());
        }
        boolean b = getBaseMapper().insert(entity) > 0;
        if (b) videoCovers.enqueue(entity.getId(), userId, entity.getCover(), false);
        return ApiResponse.success(b);
    }

    @PutMapping("/{id}")
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Boolean> update(@PathVariable Long id, @Valid @RequestBody BVideoUpdateReq entityReq) {
        BVideoEntity entity = RecordApiConvertor.INSTANCE.fromBVideoUpdateReq(entityReq);
        long userId = StpUtil.getLoginIdAsLong();

        LambdaQueryWrapper<BVideoEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BVideoEntity::getId, id);
        wrapper.eq(BVideoEntity::getUserId, userId);

        BVideoEntity existEntity = bVideoMapper.lockOwned(id, userId);
        if (existEntity == null) {
            return ApiResponse.error(ResponseCodeConst.COMMON_FAIL, "无权限更新该数据或数据不存在");
        }

        if (entityReq.getCover() != null) videoCovers.enqueue(id, userId, entityReq.getCover(), false);
        entity.setCover(null); // 封面来源及版本由导入服务在同一事务内维护。
        entity.setId(id);
        entity.setUserId(userId);
        entity.setUpdateUser(userId);
        entity.setUpdateTime(LocalDateTime.now());
        if (entity.getStatus() == ProgressStatusEnum.COMPLETED) {
            entity.setWatchedDuration(entity.getDuration());
        }

        boolean b = getBaseMapper().update(entity, wrapper) > 0;
        return ApiResponse.success(b);
    }


    @DeleteMapping("/{id}")
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Boolean> delete(@PathVariable Long id) {
        long userId = StpUtil.getLoginIdAsLong();

        LambdaQueryWrapper<BVideoEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BVideoEntity::getId, id);
        wrapper.eq(BVideoEntity::getUserId, userId);

        boolean b = getBaseMapper().delete(wrapper) > 0;
        if (!b) {
            return ApiResponse.error(ResponseCodeConst.COMMON_FAIL, "无权限删除该数据或数据不存在");
        }
        return ApiResponse.success(b);
    }

    /** 只读取导入状态，不发起外网下载；批量限制防止无限 IN 查询。 */
    @GetMapping("/covers")
    public ApiResponse<List<BVideoCoverVO>> covers(@RequestParam List<Long> ids) {
        if (ids.isEmpty() || ids.size() > 100) throw new IllegalArgumentException("每次最多查询100张封面");
        long owner = StpUtil.getLoginIdAsLong();
        var rows = bVideoMapper.selectList(new LambdaQueryWrapper<BVideoEntity>()
                .eq(BVideoEntity::getUserId, owner).in(BVideoEntity::getId, ids));
        return ApiResponse.success(rows.stream().map(v -> new BVideoCoverVO(
                v.getId(), v.getCoverFileId(), v.getCoverState())).toList());
    }

    @PostMapping("/{id}/cover/retry")
    public ApiResponse<Void> retryCover(@PathVariable Long id) {
        videoCovers.retry(id, StpUtil.getLoginIdAsLong());
        return ApiResponse.success();
    }

    @GetMapping("/getStatusCount")
    public ApiResponse<Map<String, Integer>> getStatusCount() {
        long userId = StpUtil.getLoginIdAsLong();
        List<StatusCount> statusCount = getBaseMapper().getStatusCount(userId);
        Map<String, Integer> map = statusCount.stream().collect(
                Collectors.toMap(item -> item.getStatus().getCode(), StatusCount::getCount));
        return ApiResponse.success(map);
    }

    @GetMapping("/statistics")
    public ApiResponse<BVideoStatisticsVO> statistics() {
        long userId = StpUtil.getLoginIdAsLong();
        BVideoStatisticsVO statisticsVO = new BVideoStatisticsVO();
        Integer watchTime = bVideoMapper.getWatchTime(userId, ProgressStatusEnum.COMPLETED.getCode());
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
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Boolean> tagVideo(@Valid @RequestBody BVideoCreateReq entityReq) {
        BVideoEntity entity = RecordApiConvertor.INSTANCE.fromBVideoCreateReq(entityReq);
        long userId = StpUtil.getLoginIdAsLong();
        entity.setUserId(userId);

        LambdaQueryWrapper<BVideoEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(BVideoEntity::getBvid, entity.getBvid());
        queryWrapper.eq(BVideoEntity::getUserId, userId);
        if (getBaseMapper().selectCount(queryWrapper) > 0) {
            return ApiResponse.error(ResponseCodeConst.COMMON_FAIL, "该视频已存在，无法重复添加");
        }

        entity.setCreateUser(userId);
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateUser(userId);
        entity.setUpdateTime(LocalDateTime.now());
        if (entity.getStatus() == null) {
            entity.setStatus(ProgressStatusEnum.IN_PROGRESS);
        }
        getBaseMapper().insert(entity);
        videoCovers.enqueue(entity.getId(), userId, entity.getCover(), false);
        return ApiResponse.success();
    }

    @PostMapping("/syncProgress")
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Boolean> syncProgress(@Valid @RequestBody BVideoProgressReq entityReq) {
        BVideoEntity entity = RecordApiConvertor.INSTANCE.fromBVideoProgressReq(entityReq);
        long userId = StpUtil.getLoginIdAsLong();
        entity.setUserId(userId);
        log.info("bvid: {}, currentEpisode: {}, watchedDuration: {}", entity.getBvid(),
                entity.getCurrentEpisode(), entity.getWatchedDuration());

        LambdaQueryWrapper<BVideoEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(BVideoEntity::getUserId, userId);
        queryWrapper.eq(BVideoEntity::getBvid, entity.getBvid());
        BVideoEntity exist = getBaseMapper().selectOne(queryWrapper);

        if (exist != null) {
            if (entityReq.getCover() != null) videoCovers.enqueue(exist.getId(), userId, entityReq.getCover(), false);
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
                entity.setStatus(ProgressStatusEnum.IN_PROGRESS);
            }
            getBaseMapper().insert(entity);
            videoCovers.enqueue(entity.getId(), userId, entity.getCover(), false);
            log.info("syncProgress inserted, bvid: {}", entity.getBvid());
        }
        return ApiResponse.success();
    }
}
