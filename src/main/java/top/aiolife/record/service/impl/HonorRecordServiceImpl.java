package top.aiolife.record.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.aiolife.core.query.QueryOrder;
import top.aiolife.record.mapper.IHonorRecordMapper;
import top.aiolife.record.pojo.entity.FileEntity;
import top.aiolife.record.pojo.entity.HonorRecordEntity;
import top.aiolife.record.service.IFileService;
import top.aiolife.record.service.IHonorRecordService;

/**
 * 荣誉记录服务实现
 *
 * @author Lys
 * @date 2026/04/11
 */
@Service
@RequiredArgsConstructor
public class HonorRecordServiceImpl extends ServiceImpl<IHonorRecordMapper, HonorRecordEntity>
        implements IHonorRecordService {

    private static final String BIZ_TYPE = "honor_record";

    private final IFileService fileService;

    @Override
    public List<HonorRecordEntity> listForUser(long userId) {
        LambdaQueryWrapper<HonorRecordEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HonorRecordEntity::getUserId, userId);
        QueryOrder.descNullsLast(wrapper, HonorRecordEntity::getIsTop);
        wrapper.orderByDesc(HonorRecordEntity::getHonorDate);
        List<HonorRecordEntity> list = list(wrapper);
        if (list != null) {
            list.forEach(entity -> entity.setFiles(fileService.getByBiz(BIZ_TYPE, entity.getId())));
        }
        return list;
    }

    @Override
    public HonorRecordEntity getOwned(long userId, long id) {
        HonorRecordEntity entity = getOne(new LambdaQueryWrapper<HonorRecordEntity>()
                .eq(HonorRecordEntity::getId, id)
                .eq(HonorRecordEntity::getUserId, userId));
        if (entity != null) {
            entity.setFiles(fileService.getByBiz(BIZ_TYPE, entity.getId()));
        }
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HonorRecordEntity createForUser(long userId, HonorRecordEntity entity) {
        entity.setUserId(userId);
        entity.fillCreateCommonField(userId);
        baseMapper.insert(entity);
        if (entity.getFileIds() != null && !entity.getFileIds().isEmpty()) {
            fileService.bindBizId(entity.getFileIds(), BIZ_TYPE, entity.getId());
        }
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HonorRecordEntity updateForUser(long userId, HonorRecordEntity entity) {
        entity.fillUpdateCommonField(userId);
        entity.setUserId(null);
        LambdaUpdateWrapper<HonorRecordEntity> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(HonorRecordEntity::getId, entity.getId());
        wrapper.eq(HonorRecordEntity::getUserId, userId);
        if (!update(entity, wrapper)) {
            throw new IllegalArgumentException("荣誉记录不存在或无权操作");
        }
        if (entity.getFileIds() != null) {
            List<String> fileIds = entity.getFileIds();
            LambdaUpdateWrapper<FileEntity> removedFiles = new LambdaUpdateWrapper<>();
            removedFiles.eq(FileEntity::getBizType, BIZ_TYPE)
                    .eq(FileEntity::getBizId, entity.getId())
                    .eq(FileEntity::getCreateUser, userId)
                    .notIn(!fileIds.isEmpty(), FileEntity::getId, fileIds)
                    .set(FileEntity::getBizId, null)
                    .set(FileEntity::getUpdateUser, userId)
                    .set(FileEntity::getUpdateTime, LocalDateTime.now());
            fileService.update(removedFiles);
            fileService.bindBizId(fileIds, BIZ_TYPE, entity.getId());
        }
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteForUser(long userId, List<String> ids) {
        LambdaUpdateWrapper<HonorRecordEntity> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(HonorRecordEntity::getUserId, userId);
        wrapper.in(HonorRecordEntity::getId, ids);
        wrapper.set(HonorRecordEntity::getIsDeleted, 1);
        wrapper.set(HonorRecordEntity::getUpdateTime, LocalDateTime.now());
        wrapper.set(HonorRecordEntity::getUpdateUser, userId);
        update(null, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void toggleTop(long userId, long id) {
        HonorRecordEntity record = getOne(new LambdaQueryWrapper<HonorRecordEntity>()
                .eq(HonorRecordEntity::getId, id)
                .eq(HonorRecordEntity::getUserId, userId));
        if (record == null) {
            return;
        }
        record.setIsTop(record.getIsTop() == 1 ? 0 : 1);
        record.fillUpdateCommonField(userId);
        LambdaUpdateWrapper<HonorRecordEntity> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(HonorRecordEntity::getId, id);
        wrapper.eq(HonorRecordEntity::getUserId, userId);
        update(record, wrapper);
    }
}
