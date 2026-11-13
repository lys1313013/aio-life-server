package top.aiolife.record.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.aiolife.core.constant.ResponseCodeConst;
import top.aiolife.core.constant.StatusConst;
import top.aiolife.core.exception.ApiException;
import top.aiolife.core.resq.PageResp;
import top.aiolife.record.mapper.IBVideoMapper;
import top.aiolife.record.pojo.entity.BVideoEntity;
import top.aiolife.record.pojo.enums.ProgressStatusEnum;
import top.aiolife.record.pojo.vo.BVideoCoverVO;
import top.aiolife.record.pojo.vo.BVideoStatisticsVO;
import top.aiolife.record.pojo.vo.StatusCount;
import top.aiolife.record.service.IBVideoService;
import top.aiolife.record.service.VideoCoverService;

/**
 * 哔哩哔哩学习视频 Service 实现
 *
 * @author Lys
 * @date 2026/11/13
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BVideoServiceImpl extends ServiceImpl<IBVideoMapper, BVideoEntity> implements IBVideoService {

    private static final List<String> STATUS_ORDER_CODES = List.of(
            ProgressStatusEnum.NOT_STARTED.getCode(),
            ProgressStatusEnum.IN_PROGRESS.getCode(),
            ProgressStatusEnum.ON_HOLD.getCode(),
            ProgressStatusEnum.COMPLETED.getCode());

    private final VideoCoverService videoCovers;

    @Override
    public PageResp<BVideoEntity> pageForUser(long userId, BVideoEntity condition, int page, int pageSize) {
        LambdaQueryWrapper<BVideoEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BVideoEntity::getUserId, userId);
        wrapper.eq(BVideoEntity::getIsDeleted, StatusConst.NO_DELETE);
        if (condition != null && condition.getStatus() != null) {
            wrapper.eq(BVideoEntity::getStatus, condition.getStatus());
        }
        Page<BVideoEntity> pageRequest = new Page<>(page, pageSize);
        IPage<BVideoEntity> result = baseMapper.selectPageWithStatusOrder(
                pageRequest, wrapper, STATUS_ORDER_CODES);
        return PageResp.of(result.getRecords(), result.getTotal());
    }

    @Override
    public BVideoEntity getOwned(long userId, long id) {
        BVideoEntity entity = baseMapper.selectOne(new LambdaQueryWrapper<BVideoEntity>()
                .eq(BVideoEntity::getUserId, userId)
                .eq(BVideoEntity::getId, id));
        if (entity == null) {
            throw new IllegalArgumentException("视频不存在或无权访问");
        }
        return entity;
    }

    /** 插件按 BV 号读取本人标记；未标记返回空，复用视频业务的二级锁。 */
    @Override
    public BVideoEntity findByBvid(long userId, String bvid) {
        if (bvid == null || !bvid.matches("BV[0-9A-Za-z]{10}")) {
            throw new ApiException(ResponseCodeConst.PARAM_FAIL, "BV 号格式不正确");
        }
        return baseMapper.findOwnedByBvid(userId, bvid);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createForUser(long userId, BVideoEntity entity) {
        insertForUser(userId, entity, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void tagForUser(long userId, BVideoEntity entity) {
        insertForUser(userId, entity, false);
    }

    private void insertForUser(long userId, BVideoEntity entity, boolean completeWatch) {
        entity.setUserId(userId);
        entity.fillCreateCommonField(userId);
        if (entity.getId() == null) {
            entity.setId(IdWorker.getId());
        }
        if (entity.getStatus() == null) {
            entity.setStatus(ProgressStatusEnum.IN_PROGRESS);
        }
        if (completeWatch && entity.getStatus() == ProgressStatusEnum.COMPLETED) {
            entity.setWatchedDurationSeconds(entity.getDurationSeconds());
        }
        if (baseMapper.insertIgnoreActive(entity) == 0) {
            throw new IllegalArgumentException("该视频已存在，无法重复添加");
        }
        videoCovers.enqueue(entity.getId(), userId, entity.getCover(), false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateForUser(long userId, long id, BVideoEntity entity) {
        LambdaQueryWrapper<BVideoEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BVideoEntity::getId, id);
        wrapper.eq(BVideoEntity::getUserId, userId);

        BVideoEntity existEntity = baseMapper.lockOwned(id, userId);
        if (existEntity == null) {
            throw new IllegalArgumentException("无权限更新该数据或数据不存在");
        }

        if (entity.getCover() != null) {
            videoCovers.enqueue(id, userId, entity.getCover(), false);
        }
        entity.setCover(null); // 封面来源及版本由导入服务在同一事务内维护。
        entity.setId(id);
        entity.setUserId(userId);
        entity.setUpdateUser(userId);
        entity.setUpdateTime(LocalDateTime.now());
        if (entity.getStatus() == ProgressStatusEnum.COMPLETED) {
            entity.setWatchedDurationSeconds(entity.getDurationSeconds());
        }
        return baseMapper.update(entity, wrapper) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteForUser(long userId, long id) {
        LambdaQueryWrapper<BVideoEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BVideoEntity::getId, id);
        wrapper.eq(BVideoEntity::getUserId, userId);

        boolean deleted = baseMapper.delete(wrapper) > 0;
        if (!deleted) {
            throw new IllegalArgumentException("无权限删除该数据或数据不存在");
        }
        return deleted;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncProgress(long userId, BVideoEntity entity) {
        entity.setUserId(userId);
        log.info("bvid: {}, currentEpisode: {}, watchedDuration: {}", entity.getBvid(),
                entity.getCurrentEpisode(), entity.getWatchedDurationSeconds());

        BVideoEntity exist = baseMapper.findOwnedByBvid(userId, entity.getBvid());
        Long videoId;
        if (exist != null) {
            updateProgress(exist.getId(), userId, entity);
            videoId = exist.getId();
            log.info("syncProgress updated, id: {}", videoId);
        } else {
            entity.fillCreateCommonField(userId);
            if (entity.getId() == null) {
                entity.setId(IdWorker.getId());
            }
            if (entity.getStatus() == null) {
                entity.setStatus(ProgressStatusEnum.IN_PROGRESS);
            }
            entity.setLastWatched(LocalDateTime.now());
            if (baseMapper.insertIgnoreActive(entity) > 0) {
                videoId = entity.getId();
                log.info("syncProgress inserted, bvid: {}", entity.getBvid());
            } else {
                // 并发创建同一 BV 号时后到请求改为更新已提交记录。
                exist = baseMapper.findOwnedByBvid(userId, entity.getBvid());
                if (exist == null) {
                    throw new IllegalStateException("视频记录写入冲突，请稍后重试");
                }
                updateProgress(exist.getId(), userId, entity);
                videoId = exist.getId();
                log.info("syncProgress updated after concurrent insert, id: {}", videoId);
            }
        }
        if (entity.getCover() != null) {
            videoCovers.enqueue(videoId, userId, entity.getCover(), false);
        }
    }

    private void updateProgress(Long id, long userId, BVideoEntity entity) {
        LambdaUpdateWrapper<BVideoEntity> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.set(BVideoEntity::getCurrentEpisode, entity.getCurrentEpisode());
        updateWrapper.set(BVideoEntity::getWatchedDurationSeconds, entity.getWatchedDurationSeconds());
        updateWrapper.set(BVideoEntity::getLastWatched, LocalDateTime.now());
        updateWrapper.set(BVideoEntity::getUpdateTime, LocalDateTime.now());
        updateWrapper.set(BVideoEntity::getUpdateUser, userId);
        updateWrapper.eq(BVideoEntity::getId, id);
        baseMapper.update(null, updateWrapper);
    }

    /** 只读取导入状态，不发起外网下载；批量限制防止无限 IN 查询。 */
    @Override
    public List<BVideoCoverVO> listCovers(long userId, List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > 100) {
            throw new IllegalArgumentException("每次最多查询100张封面");
        }
        var rows = baseMapper.selectList(new LambdaQueryWrapper<BVideoEntity>()
                .eq(BVideoEntity::getUserId, userId).in(BVideoEntity::getId, ids));
        return rows.stream().map(v -> new BVideoCoverVO(
                v.getId(), v.getCoverFileId(), v.getCoverState())).toList();
    }

    @Override
    public void retryCover(long userId, long id) {
        videoCovers.retry(id, userId);
    }

    @Override
    public Map<String, Integer> statusCount(long userId) {
        List<StatusCount> statusCount = baseMapper.getStatusCount(userId);
        return statusCount.stream().collect(
                Collectors.toMap(item -> item.getStatus().getCode(), StatusCount::getCount));
    }

    @Override
    public BVideoStatisticsVO statistics(long userId) {
        BVideoStatisticsVO statisticsVO = new BVideoStatisticsVO();
        Integer watchTime = baseMapper.getWatchTime(userId, ProgressStatusEnum.COMPLETED.getCode());
        Integer totalTime = baseMapper.getTotalTime(userId);
        if (watchTime != null) {
            statisticsVO.setStudiedSeconds(watchTime);
        }
        if (totalTime != null) {
            statisticsVO.setTotalSeconds(totalTime);
        }
        int totalTimeValue = (totalTime != null) ? totalTime : 0;
        int watchedTimeValue = (watchTime != null) ? watchTime : 0;
        statisticsVO.setUnstudiedSeconds(totalTimeValue - watchedTimeValue);
        return statisticsVO;
    }
}
