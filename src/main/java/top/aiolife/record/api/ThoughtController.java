package top.aiolife.record.api;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import top.aiolife.core.query.CommonQuery;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.resq.PageResp;
import top.aiolife.core.util.SysUtil;
import top.aiolife.record.mapper.IRelaEventMapper;
import top.aiolife.record.mapper.IThoughtMapper;
import top.aiolife.record.pojo.entity.ThoughtRelaEventEntity;
import top.aiolife.record.pojo.entity.ThoughtEntity;
import top.aiolife.record.pojo.req.CommonReq;
import top.aiolife.record.pojo.req.ThoughtSaveEventReq;
import top.aiolife.record.pojo.req.ThoughtSaveReq;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

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

    @PostMapping("/query")
    public ApiResponse<PageResp<ThoughtEntity>> query(
            @RequestBody CommonQuery<ThoughtEntity> query) {
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

        return ApiResponse.success(objectPageResp);
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
    public ApiResponse<Boolean> update(@PathVariable("id") Long id, @RequestBody ThoughtEntity entity) {
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
                entity.getEvents().forEach(eventEntity -> {
                    eventEntity.setThoughtId(entity.getId());
                    relaEventMapper.insertOrUpdate(eventEntity);
                });
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
    public ApiResponse<List<ThoughtEntity>> dashboardThoughts() {
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
        
        return ApiResponse.success(list);
    }
}
