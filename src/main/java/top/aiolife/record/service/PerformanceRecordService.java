package top.aiolife.record.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.aiolife.core.resq.PageResp;
import top.aiolife.record.mapper.IPerformanceMapper;
import top.aiolife.record.pojo.entity.FileEntity;
import top.aiolife.record.pojo.entity.PerformanceEntity;

/** 演出记录的持久化、所有权校验和附件事务。 */
@Service
@RequiredArgsConstructor
public class PerformanceRecordService {

    private static final String BIZ_TYPE = "performance";

    private final IPerformanceMapper performanceMapper;
    private final IFileService fileService;

    public PageResp<PerformanceEntity> list(long userId, long page, long pageSize) {
        var result = performanceMapper.selectPage(new Page<PerformanceEntity>(page, pageSize),
                new LambdaQueryWrapper<PerformanceEntity>().eq(PerformanceEntity::getUserId, userId));
        if (result.getRecords() != null) {
            for (PerformanceEntity entity : result.getRecords()) {
                entity.setFiles(fileService.getByBiz(BIZ_TYPE, entity.getId()));
            }
        }
        return PageResp.of(result.getRecords(), result.getTotal());
    }

    @Transactional(rollbackFor = Exception.class)
    public PerformanceEntity create(PerformanceEntity entity, long userId) {
        entity.setId(null);
        entity.setUserId(userId);
        entity.fillCreateCommonField(userId);
        performanceMapper.insert(entity);
        syncFiles(entity, userId);
        return entity;
    }

    @Transactional(rollbackFor = Exception.class)
    public PerformanceEntity update(PerformanceEntity entity, long userId) {
        entity.setUserId(null);
        entity.setCreateUser(null);
        entity.setCreateTime(null);
        entity.fillUpdateCommonField(userId);
        var wrapper = new LambdaQueryWrapper<PerformanceEntity>()
                .eq(PerformanceEntity::getId, entity.getId())
                .eq(PerformanceEntity::getUserId, userId);
        if (performanceMapper.update(entity, wrapper) == 0) {
            throw new IllegalArgumentException("演出记录不存在或无权操作");
        }
        syncFiles(entity, userId);
        return entity;
    }

    public boolean delete(long id, long userId) {
        return performanceMapper.delete(new LambdaQueryWrapper<PerformanceEntity>()
                .eq(PerformanceEntity::getId, id)
                .eq(PerformanceEntity::getUserId, userId)) > 0;
    }

    // 未传保留附件，空列表解除关联；附件实体本身不删除。
    private void syncFiles(PerformanceEntity entity, long userId) {
        if (entity.getFileIds() == null) return;
        List<String> fileIds = entity.getFileIds().stream().distinct().toList();
        if (!fileIds.isEmpty()) {
            List<FileEntity> ownedFiles = fileService.list(new LambdaQueryWrapper<FileEntity>()
                    .in(FileEntity::getId, fileIds)
                    .eq(FileEntity::getCreateUser, userId));
            if (ownedFiles.size() != fileIds.size()) {
                throw new IllegalArgumentException("附件不存在或无权操作");
            }
        }
        var removedFiles = new LambdaUpdateWrapper<FileEntity>()
                .eq(FileEntity::getBizType, BIZ_TYPE)
                .eq(FileEntity::getBizId, entity.getId())
                .eq(FileEntity::getCreateUser, userId)
                .notIn(!fileIds.isEmpty(), FileEntity::getId, fileIds)
                .set(FileEntity::getBizId, null)
                .set(FileEntity::getUpdateUser, userId)
                .set(FileEntity::getUpdateTime, LocalDateTime.now());
        fileService.update(removedFiles);
        fileService.bindBizId(fileIds, BIZ_TYPE, entity.getId());
    }
}
