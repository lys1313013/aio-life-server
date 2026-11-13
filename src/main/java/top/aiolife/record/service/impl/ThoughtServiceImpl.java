package top.aiolife.record.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.aiolife.core.mybatis.IlikeQuery;
import top.aiolife.core.query.QueryOrder;
import top.aiolife.core.resq.PageResp;
import top.aiolife.core.util.SysUtil;
import top.aiolife.record.mapper.IRelaEventMapper;
import top.aiolife.record.mapper.IThoughtMapper;
import top.aiolife.record.pojo.entity.ThoughtEntity;
import top.aiolife.record.pojo.entity.ThoughtRelaEventEntity;
import top.aiolife.record.service.IThoughtService;

/**
 * 闪念查询与写入服务。
 */
@Service
@RequiredArgsConstructor
public class ThoughtServiceImpl extends ServiceImpl<IThoughtMapper, ThoughtEntity> implements IThoughtService {

    private final IThoughtMapper thoughtMapper;
    private final IRelaEventMapper relaEventMapper;

    @Override
    public PageResp<ThoughtEntity> pageForUser(long userId, ThoughtEntity condition, int page, int pageSize) {
        LambdaQueryWrapper<ThoughtEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ThoughtEntity::getUserId, userId);
        if (condition != null) {
            wrapper.eq(condition.getId() != null, ThoughtEntity::getId, condition.getId());
            wrapper.eq(condition.getIsPinned() != null, ThoughtEntity::getIsPinned, condition.getIsPinned());
            wrapper.eq(condition.getHiddenContent() != null, ThoughtEntity::getHiddenContent,
                    condition.getHiddenContent());
            IlikeQuery.anyOf(wrapper, SysUtil.isNotEmpty(condition.getContent()),
                    condition.getContent(), ThoughtEntity::getContent);
        }
        QueryOrder.descNullsLast(wrapper, ThoughtEntity::getUpdateTime, ThoughtEntity::getId);
        Page<ThoughtEntity> result = thoughtMapper.selectPage(new Page<>(page, pageSize), wrapper);
        attachEvents(result.getRecords());
        return PageResp.of(result.getRecords(), result.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateForUser(long userId, long id, ThoughtEntity entity) {
        entity.setId(id);
        entity.setUserId(userId);
        if (entity.getContent() != null) {
            entity.setUpdateTime(LocalDateTime.now());
        }
        LambdaQueryWrapper<ThoughtEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ThoughtEntity::getId, id);
        wrapper.eq(ThoughtEntity::getUserId, userId);
        if (thoughtMapper.update(entity, wrapper) == 0) {
            throw new IllegalArgumentException("无权操作或记录不存在");
        }
        if (entity.getEvents() == null) {
            return;
        }
        List<Long> retainedEventIds = new ArrayList<>();
        for (ThoughtRelaEventEntity event : entity.getEvents()) {
            ThoughtRelaEventEntity update = new ThoughtRelaEventEntity();
            update.setContent(event.getContent());
            if (event.getId() == null) {
                update.setThoughtId(id);
                update.fillCreateCommonField(userId);
                relaEventMapper.insert(update);
                retainedEventIds.add(update.getId());
            } else {
                update.fillUpdateCommonField(userId);
                int updated = relaEventMapper.update(update, new LambdaQueryWrapper<ThoughtRelaEventEntity>()
                        .eq(ThoughtRelaEventEntity::getId, event.getId())
                        .eq(ThoughtRelaEventEntity::getThoughtId, id));
                if (updated == 0) {
                    throw new IllegalArgumentException("关联事件不存在或无权操作");
                }
                retainedEventIds.add(event.getId());
            }
        }
        relaEventMapper.delete(new LambdaQueryWrapper<ThoughtRelaEventEntity>()
                .eq(ThoughtRelaEventEntity::getThoughtId, id)
                .notIn(!retainedEventIds.isEmpty(), ThoughtRelaEventEntity::getId, retainedEventIds));
    }

    @Override
    public void deleteForUser(long userId, List<String> ids) {
        LambdaUpdateWrapper<ThoughtEntity> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(ThoughtEntity::getUserId, userId);
        wrapper.in(ThoughtEntity::getId, ids);
        thoughtMapper.delete(wrapper);
    }

    @Override
    public List<ThoughtEntity> listPinnedForUser(long userId) {
        LambdaQueryWrapper<ThoughtEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ThoughtEntity::getUserId, userId);
        wrapper.eq(ThoughtEntity::getIsPinned, 1);
        QueryOrder.descNullsLast(wrapper, ThoughtEntity::getUpdateTime, ThoughtEntity::getId);
        List<ThoughtEntity> list = thoughtMapper.selectList(wrapper);
        attachEvents(list);
        return list;
    }

    private void attachEvents(List<ThoughtEntity> thoughts) {
        List<Long> thoughtIds = thoughts.stream().map(ThoughtEntity::getId).toList();
        if (thoughtIds.isEmpty()) {
            return;
        }
        List<ThoughtRelaEventEntity> events = relaEventMapper.selectList(
                new LambdaQueryWrapper<ThoughtRelaEventEntity>()
                        .in(ThoughtRelaEventEntity::getThoughtId, thoughtIds));
        thoughts.forEach(thought -> thought.setEvents(events.stream()
                .filter(event -> event.getThoughtId().equals(thought.getId()))
                .toList()));
    }
}
