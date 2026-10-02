package top.aiolife.record.api;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.aiolife.core.query.CommonQuery;
import top.aiolife.core.query.QueryParams;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.resq.PageResp;
import top.aiolife.core.util.SysUtil;
import top.aiolife.record.convertor.RecordApiConvertor;
import top.aiolife.record.mapper.IRelaEventMapper;
import top.aiolife.record.mapper.IThoughtMapper;
import top.aiolife.record.pojo.entity.ThoughtEntity;
import top.aiolife.record.pojo.entity.ThoughtRelaEventEntity;
import top.aiolife.record.pojo.query.ThoughtQuery;
import top.aiolife.record.pojo.req.CommonReq;
import top.aiolife.record.pojo.req.ThoughtSaveEventReq;
import top.aiolife.record.pojo.req.ThoughtSaveReq;
import top.aiolife.record.pojo.req.ThoughtUpdateReq;
import top.aiolife.record.pojo.vo.ThoughtRecordVO;

/**
 * 类功能描述
 *
 * @author Lys
 * @date 2025-11-16 17:01
 */
@Slf4j
@RestController
@AllArgsConstructor
@RequestMapping("/thought")
public class ThoughtController {
    private final IThoughtMapper thoughtMapper;

    private final IRelaEventMapper relaEventMapper;

    public IThoughtMapper getBaseMapper() {
        return thoughtMapper;
    }

    @GetMapping("/query")
    public ApiResponse<PageResp<ThoughtRecordVO>> query(
            @QueryParams CommonQuery<ThoughtQuery> requestQuery) {
        CommonQuery<ThoughtEntity> query = requestQuery.map(RecordApiConvertor.INSTANCE::fromThoughtQuery);
        long userId = StpUtil.getLoginIdAsLong();
        LambdaQueryWrapper<ThoughtEntity> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(ThoughtEntity::getUserId, userId);
        ThoughtEntity condition = query.getCondition();
        if (condition != null) {
            lambdaQueryWrapper.eq(condition.getId() != null, ThoughtEntity::getId, condition.getId());
            lambdaQueryWrapper.eq(condition.getIsPinned() != null, ThoughtEntity::getIsPinned, condition.getIsPinned());
            lambdaQueryWrapper.eq(condition.getHiddenContent() != null, ThoughtEntity::getHiddenContent,
                    condition.getHiddenContent());
            lambdaQueryWrapper.like(SysUtil.isNotEmpty(condition.getContent()), ThoughtEntity::getContent,
                    condition.getContent());
        }

        lambdaQueryWrapper.orderByDesc(ThoughtEntity::getUpdateTime);
        Page<ThoughtEntity> page = new Page<>(query.getPage(), query.getPageSize());
        IPage<ThoughtEntity> iPage = thoughtMapper.selectPage(page, lambdaQueryWrapper);


        // 查询明细
        List<Long> thoughtIdList = iPage.getRecords().stream().map(ThoughtEntity::getId).toList();
        if (!thoughtIdList.isEmpty()) {
            LambdaQueryWrapper<ThoughtRelaEventEntity> relaEventLambdaQueryWrapper = new LambdaQueryWrapper<>();
            relaEventLambdaQueryWrapper.in(ThoughtRelaEventEntity::getThoughtId, thoughtIdList);
            List<ThoughtRelaEventEntity> thoughtRelaEventEntityList = relaEventMapper.selectList(relaEventLambdaQueryWrapper);
            // 关联事件
            iPage.getRecords().forEach(thoughtVO -> {
                List<ThoughtRelaEventEntity> eventEntityList = thoughtRelaEventEntityList.stream().filter(eventEntity -> eventEntity.getThoughtId().equals(thoughtVO.getId())).toList();
                thoughtVO.setEvents(eventEntityList);
            });
        }

        PageResp<ThoughtEntity> objectPageResp = PageResp.of(iPage.getRecords(), iPage.getTotal());

        return ApiResponse.success(RecordApiConvertor.INSTANCE.toThoughtRecordVOPage(objectPageResp));
    }

    @PostMapping
    public ApiResponse<Boolean> save(@RequestBody ThoughtSaveReq req) {
        Long loginId = StpUtil.getLoginIdAsLong();
        ThoughtEntity entity = new ThoughtEntity();
        entity.setContent(req.getContent());
        entity.setUserId(loginId);
        entity.setCreateUser(loginId);
        entity.setUpdateTime(LocalDateTime.now());
        if (req.getIsPinned() != null) {
            entity.setIsPinned(req.getIsPinned());
        }
        getBaseMapper().insert(entity);
        List<ThoughtSaveEventReq> events = req.getEvents();
        if (events != null) {
            events.forEach(eventReq -> {
                ThoughtRelaEventEntity eventEntity = new ThoughtRelaEventEntity();
                eventEntity.setThoughtId(entity.getId());
                eventEntity.setContent(eventReq.getContent());
                relaEventMapper.insert(eventEntity);
            });
        }
        return ApiResponse.success(true);
    }

    @PutMapping("/{id}")
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Boolean> update(@PathVariable("id") Long id, @Valid @RequestBody ThoughtUpdateReq entityReq) {
        ThoughtEntity entity = RecordApiConvertor.INSTANCE.fromThoughtUpdateReq(entityReq);
        Long userId = StpUtil.getLoginIdAsLong();
        entity.setId(id);
        entity.setUserId(userId);

        // 只有在修改内容时才更新时间，单纯点击隐藏内容不更新时间
        if (entity.getContent() != null) {
            entity.setUpdateTime(LocalDateTime.now());
        }

        LambdaQueryWrapper<ThoughtEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ThoughtEntity::getId, entity.getId());
        wrapper.eq(ThoughtEntity::getUserId, userId);

        int rows = getBaseMapper().update(entity, wrapper);

        if (rows > 0) {
            // 更新事件
            if (entity.getEvents() != null) {
                List<Long> retainedEventIds = new ArrayList<>();
                entity.getEvents().forEach(eventEntity -> {
                    ThoughtRelaEventEntity update = new ThoughtRelaEventEntity();
                    update.setContent(eventEntity.getContent());
                    if (eventEntity.getId() == null) {
                        update.setThoughtId(id);
                        update.fillCreateCommonField(userId);
                        relaEventMapper.insert(update);
                        retainedEventIds.add(update.getId());
                    } else {
                        update.fillUpdateCommonField(userId);
                        int updated = relaEventMapper.update(update,
                                new LambdaQueryWrapper<ThoughtRelaEventEntity>()
                                        .eq(ThoughtRelaEventEntity::getId, eventEntity.getId())
                                        .eq(ThoughtRelaEventEntity::getThoughtId, id));
                        if (updated == 0) {
                            throw new IllegalArgumentException("关联事件不存在或无权操作");
                        }
                        retainedEventIds.add(eventEntity.getId());
                    }
                });
                // events 是保存后的完整事件列表；未传保留，空列表清空。
                // 只清理已验证归属的闪念，保留旧事件 ID 和新增事件 ID。
                relaEventMapper.delete(new LambdaQueryWrapper<ThoughtRelaEventEntity>()
                        .eq(ThoughtRelaEventEntity::getThoughtId, id)
                        .notIn(!retainedEventIds.isEmpty(), ThoughtRelaEventEntity::getId, retainedEventIds));
            }
            return ApiResponse.success(true);
        }
        return ApiResponse.error("无权操作或记录不存在");
    }

    /**
     * 批量删除
     */
    @PostMapping("/batchDelete")
    public ApiResponse<Boolean> delete(@RequestBody CommonReq CommonReq) {
        LambdaUpdateWrapper<ThoughtEntity> lambdaUpdateWrapper = new LambdaUpdateWrapper<>();
        lambdaUpdateWrapper.eq(ThoughtEntity::getUserId, StpUtil.getLoginIdAsLong());
        lambdaUpdateWrapper.in(ThoughtEntity::getId, CommonReq.getIdList());
        getBaseMapper().delete(lambdaUpdateWrapper);
        return ApiResponse.success(true);
    }

    /**
     * 获取看板展示的闪念列表
     */
    @GetMapping("/dashboard")
    public ApiResponse<List<ThoughtRecordVO>> dashboardThoughts() {
        long userId = StpUtil.getLoginIdAsLong();
        LambdaQueryWrapper<ThoughtEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ThoughtEntity::getUserId, userId);
        wrapper.eq(ThoughtEntity::getIsPinned, 1);
        wrapper.orderByDesc(ThoughtEntity::getUpdateTime);
        List<ThoughtEntity> list = getBaseMapper().selectList(wrapper);

        // 查询明细
        List<Long> thoughtIdList = list.stream().map(ThoughtEntity::getId).toList();
        if (!thoughtIdList.isEmpty()) {
            LambdaQueryWrapper<ThoughtRelaEventEntity> relaEventLambdaQueryWrapper = new LambdaQueryWrapper<>();
            relaEventLambdaQueryWrapper.in(ThoughtRelaEventEntity::getThoughtId, thoughtIdList);
            List<ThoughtRelaEventEntity> thoughtRelaEventEntityList = relaEventMapper.selectList(relaEventLambdaQueryWrapper);
            // 关联事件
            list.forEach(thoughtVO -> {
                List<ThoughtRelaEventEntity> eventEntityList = thoughtRelaEventEntityList.stream()
                        .filter(eventEntity -> eventEntity.getThoughtId().equals(thoughtVO.getId())).toList();
                thoughtVO.setEvents(eventEntityList);
            });
        }

        return ApiResponse.success(RecordApiConvertor.INSTANCE.toThoughtRecordVOList(list));
    }
}
