package top.aiolife.record.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import top.aiolife.core.query.PaginationRequest;
import top.aiolife.core.resq.PageResp;
import top.aiolife.record.mapper.IEventRecordMapper;
import top.aiolife.record.pojo.entity.EventRecordEntity;
import top.aiolife.record.pojo.entity.FileEntity;

/** 活动记录的持久化、所有权校验和附件事务。 */
@Service
@RequiredArgsConstructor
public class EventRecordService {

    private static final String BIZ_TYPE = "event_record";

    private final IEventRecordMapper eventRecordMapper;
    private final IFileService fileService;

    public PageResp<EventRecordEntity> list(long userId, long page, long pageSize) {
        try {
            PaginationRequest.validate(page, pageSize, PaginationRequest.MAX_PAGE_SIZE);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
        var result = eventRecordMapper.selectPage(new Page<EventRecordEntity>(page, pageSize),
                new LambdaQueryWrapper<EventRecordEntity>()
                        .eq(EventRecordEntity::getUserId, userId)
                        .orderByDesc(EventRecordEntity::getEventDate, EventRecordEntity::getId));
        if (result.getRecords() != null) {
            for (EventRecordEntity entity : result.getRecords()) {
                entity.setFiles(fileService.getByBiz(BIZ_TYPE, entity.getId()));
            }
        }
        return PageResp.of(result.getRecords(), result.getTotal());
    }

    @Transactional(rollbackFor = Exception.class)
    public EventRecordEntity create(EventRecordEntity entity, long userId) {
        entity.setId(null);
        entity.setUserId(userId);
        entity.fillCreateCommonField(userId);
        eventRecordMapper.insert(entity);
        syncFiles(entity, userId);
        return entity;
    }

    @Transactional(rollbackFor = Exception.class)
    public EventRecordEntity update(EventRecordEntity entity, long userId) {
        entity.setUserId(null);
        entity.setCreateUser(null);
        entity.setCreateTime(null);
        entity.fillUpdateCommonField(userId);
        var wrapper = new LambdaQueryWrapper<EventRecordEntity>()
                .eq(EventRecordEntity::getId, entity.getId())
                .eq(EventRecordEntity::getUserId, userId);
        if (eventRecordMapper.update(entity, wrapper) == 0) {
            throw new IllegalArgumentException("活动记录不存在或无权操作");
        }
        syncFiles(entity, userId);
        return entity;
    }

    public boolean delete(long id, long userId) {
        return eventRecordMapper.delete(new LambdaQueryWrapper<EventRecordEntity>()
                .eq(EventRecordEntity::getId, id)
                .eq(EventRecordEntity::getUserId, userId)) > 0;
    }

    // 未传保留附件，空列表解除关联；附件实体本身不删除。
    private void syncFiles(EventRecordEntity entity, long userId) {
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
