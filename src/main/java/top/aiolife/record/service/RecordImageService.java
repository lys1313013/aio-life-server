package top.aiolife.record.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.beans.BeanUtils;
import org.springframework.transaction.annotation.Transactional;
import top.aiolife.record.enums.AttachmentBizType;
import top.aiolife.core.id.AttachmentKey;
import top.aiolife.record.mapper.IAttachmentMapper;
import top.aiolife.record.mapper.RecordImageMapper;
import top.aiolife.record.pojo.entity.AttachmentEntity;

/** 四类单图业务统一使用 attachment.biz_type/biz_id，不在业务表保存文件引用。 */
@Service
@RequiredArgsConstructor
public class RecordImageService {
    private final IAttachmentMapper files;
    private final RecordImageMapper records;
    private final VideoCoverStorage storage;

    /** null 或空白清除当前图片；替换只解除旧关联，不删除存储对象。 */
    @Transactional(rollbackFor = Exception.class)
    public String replace(long userId, AttachmentBizType type, long bizId, String fileId) {
        requireSupported(type);
        String bizType = type.getBizType();
        if (records.lockOwned(userId, bizType, bizId) == null) {
            throw new IllegalArgumentException("记录不存在或无权限");
        }
        String selected = fileId == null || fileId.isBlank() ? null : fileId;
        if (selected != null) {
            AttachmentKey.require(selected);
            AttachmentEntity file = files.selectForBindingByKey(selected);
            if (file == null || !Objects.equals(file.getUserId(), userId)
                    || !bizType.equals(file.getBizType())) {
                throw new IllegalArgumentException("图片不存在、类型不匹配或无权限");
            }
            storage.hold(file.getStorageObjectId());
            if (file.getBizId() != null && !Objects.equals(file.getBizId(), bizId)) {
                // 同一商品多件可复用图片字节，每件仍有独立的附件归属，不能迁走原记录的图片。
                var copy = new AttachmentEntity();
                BeanUtils.copyProperties(file, copy);
                copy.setId(null);
                copy.setFileKey(null);
                copy.setBizId(null);
                copy.fillCreateCommonField(userId);
                files.insert(copy);
                selected = copy.getFileKey();
            }
        }
        var detach = new LambdaUpdateWrapper<AttachmentEntity>()
                .eq(AttachmentEntity::getBizType, bizType).eq(AttachmentEntity::getBizId, bizId)
                .eq(AttachmentEntity::getUserId, userId)
                .ne(selected != null, AttachmentEntity::getFileKey, selected)
                .set(AttachmentEntity::getBizId, null)
                .set(AttachmentEntity::getUpdateUser, userId).set(AttachmentEntity::getUpdateTime, LocalDateTime.now());
        files.update(null, detach);
        if (selected != null) {
            files.update(null, new LambdaUpdateWrapper<AttachmentEntity>()
                    .eq(AttachmentEntity::getFileKey, selected).eq(AttachmentEntity::getUserId, userId)
                    .set(AttachmentEntity::getBizId, bizId)
                    .set(AttachmentEntity::getUpdateUser, userId).set(AttachmentEntity::getUpdateTime, LocalDateTime.now()));
        }
        return selected;
    }

    /** 删除业务时在同一个业务记录行锁下软删附件，字节与成功来源继续保留。 */
    @Transactional(rollbackFor=Exception.class)
    public void delete(long userId,AttachmentBizType type,long bizId) {
        requireSupported(type);
        if(records.lockOwned(userId,type.getBizType(),bizId)==null) return;
        files.update(null,new LambdaUpdateWrapper<AttachmentEntity>().eq(AttachmentEntity::getUserId,userId)
            .eq(AttachmentEntity::getBizType,type.getBizType()).eq(AttachmentEntity::getBizId,bizId)
            .set(AttachmentEntity::getIsDeleted,1).set(AttachmentEntity::getIsPublic,0)
            .set(AttachmentEntity::getUpdateUser,userId).set(AttachmentEntity::getUpdateTime,LocalDateTime.now()));
    }

    public String find(long userId, AttachmentBizType type, long bizId) {
        return findAll(userId, type, List.of(bizId)).get(bizId);
    }

    /** 每批只查一次图片，避免列表逐条查询。 */
    public Map<Long, String> findAll(long userId, AttachmentBizType type, Collection<Long> bizIds) {
        requireSupported(type);
        if (bizIds.isEmpty()) return Map.of();
        var result = new HashMap<Long, String>();
        files.selectList(new LambdaQueryWrapper<AttachmentEntity>()
                .select(AttachmentEntity::getFileKey, AttachmentEntity::getBizId)
                .eq(AttachmentEntity::getUserId, userId).eq(AttachmentEntity::getBizType, type.getBizType())
                .in(AttachmentEntity::getBizId, bizIds))
                .forEach(file -> result.put(file.getBizId(), file.getFileKey()));
        return result;
    }

    public <T> void fill(long userId, AttachmentBizType type, List<T> rows,
                         Function<T, Long> id, BiConsumer<T, String> setFileId) {
        var images = findAll(userId, type, rows.stream().map(id).toList());
        rows.forEach(row -> setFileId.accept(row, images.get(id.apply(row))));
    }

    private static void requireSupported(AttachmentBizType type) {
        if (type != AttachmentBizType.DEVICE && type != AttachmentBizType.MOVIE
                && type != AttachmentBizType.READ_RECORD && type != AttachmentBizType.WARDROBE_ITEM) {
            throw new IllegalArgumentException("不支持的单图业务类型");
        }
    }
}
