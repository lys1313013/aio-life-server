package top.aiolife.record.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.aiolife.core.mybatis.IlikeQuery;
import top.aiolife.core.query.QueryOrder;
import top.aiolife.core.resq.PageResp;
import top.aiolife.record.mapper.IMemoMapper;
import top.aiolife.record.pojo.entity.MemoEntity;
import top.aiolife.record.service.IMemoService;

/**
 * 备忘录Service实现
 *
 * @author Lys
 * @date 2025/12/07 14:35
 */
@Service
public class MemoServiceImpl extends ServiceImpl<IMemoMapper, MemoEntity> implements IMemoService {

    @Override
    public PageResp<MemoEntity> pageForUser(long userId, String content, int page, int pageSize) {
        LambdaQueryWrapper<MemoEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MemoEntity::getUserId, userId);
        IlikeQuery.anyOf(wrapper, content != null, content, MemoEntity::getContent);
        QueryOrder.descNullsLast(wrapper, MemoEntity::getUpdateTime, MemoEntity::getId);
        Page<MemoEntity> result = baseMapper.selectPage(new Page<>(page, pageSize), wrapper);
        return PageResp.of(result.getRecords(), result.getTotal());
    }

    @Override
    public MemoEntity getOwned(long userId, long id) {
        MemoEntity entity = baseMapper.selectOne(new LambdaQueryWrapper<MemoEntity>()
                .eq(MemoEntity::getUserId, userId)
                .eq(MemoEntity::getId, id));
        if (entity == null) {
            throw new IllegalArgumentException("笔记不存在或无权访问");
        }
        return entity;
    }

    @Override
    @Transactional
    public MemoEntity createForUser(long userId, MemoEntity entity) {
        entity.setUserId(userId);
        entity.setCreateUser(userId);
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        baseMapper.insert(entity);
        return entity;
    }

    @Override
    @Transactional
    public void updateForUser(long userId, long id, MemoEntity entity) {
        entity.setId(id);
        entity.setUserId(userId);
        entity.setUpdateUser(userId);
        if (entity.getContent() != null || entity.getTitle() != null) {
            entity.setUpdateTime(LocalDateTime.now());
        }
        LambdaQueryWrapper<MemoEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MemoEntity::getId, id);
        wrapper.eq(MemoEntity::getUserId, userId);
        baseMapper.update(entity, wrapper);
    }

    @Override
    public void deleteForUser(long userId, long id) {
        baseMapper.delete(new LambdaQueryWrapper<MemoEntity>()
                .eq(MemoEntity::getId, id)
                .eq(MemoEntity::getUserId, userId));
    }
}
