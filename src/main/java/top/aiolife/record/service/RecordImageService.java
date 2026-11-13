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
import top.aiolife.record.enums.FileBizType;
import top.aiolife.record.mapper.IFileMapper;
import top.aiolife.record.mapper.RecordImageMapper;
import top.aiolife.record.pojo.entity.FileEntity;

/** 四类单图业务统一使用 file.biz_type/biz_id，不在业务表保存文件引用。 */
@Service
@RequiredArgsConstructor
public class RecordImageService {
    private final IFileMapper files;
    private final RecordImageMapper records;

    /** null 或空白清除当前图片；替换只解除旧关联，不删除存储对象。 */
    @Transactional(rollbackFor = Exception.class)
    public String replace(long userId, FileBizType type, long bizId, String fileId) {
        requireSupported(type);
        String bizType = type.getBizType();
        if (records.lockOwned(userId, bizType, bizId) == null) {
            throw new IllegalArgumentException("记录不存在或无权限");
        }
        String selected = fileId == null || fileId.isBlank() ? null : fileId;
        if (selected != null) {
            FileEntity file = files.selectForAvatarBinding(selected);
            if (file == null || !Objects.equals(file.getCreateUser(), userId)
                    || !bizType.equals(file.getBizType())) {
                throw new IllegalArgumentException("图片不存在、类型不匹配或无权限");
            }
            if (file.getBizId() != null && !Objects.equals(file.getBizId(), bizId)) {
                // 同一商品多件可复用图片字节，每件仍有独立的附件归属，不能迁走原记录的图片。
                var copy = new FileEntity();
                BeanUtils.copyProperties(file, copy);
                copy.setId(null);
                copy.setBizId(null);
                copy.fillCreateCommonField(userId);
                files.insert(copy);
                selected = copy.getId();
            }
        }
        var detach = new LambdaUpdateWrapper<FileEntity>()
                .eq(FileEntity::getBizType, bizType).eq(FileEntity::getBizId, bizId)
                .eq(FileEntity::getCreateUser, userId)
                .ne(selected != null, FileEntity::getId, selected)
                .set(FileEntity::getBizId, null)
                .set(FileEntity::getUpdateUser, userId).set(FileEntity::getUpdateTime, LocalDateTime.now());
        files.update(null, detach);
        if (selected != null) {
            files.update(null, new LambdaUpdateWrapper<FileEntity>()
                    .eq(FileEntity::getId, selected).eq(FileEntity::getCreateUser, userId)
                    .set(FileEntity::getBizId, bizId)
                    .set(FileEntity::getUpdateUser, userId).set(FileEntity::getUpdateTime, LocalDateTime.now()));
        }
        return selected;
    }

    public String find(long userId, FileBizType type, long bizId) {
        return findAll(userId, type, List.of(bizId)).get(bizId);
    }

    /** 每批只查一次图片，避免列表逐条查询。 */
    public Map<Long, String> findAll(long userId, FileBizType type, Collection<Long> bizIds) {
        requireSupported(type);
        if (bizIds.isEmpty()) return Map.of();
        var result = new HashMap<Long, String>();
        files.selectList(new LambdaQueryWrapper<FileEntity>()
                .select(FileEntity::getId, FileEntity::getBizId)
                .eq(FileEntity::getCreateUser, userId).eq(FileEntity::getBizType, type.getBizType())
                .in(FileEntity::getBizId, bizIds))
                .forEach(file -> result.put(file.getBizId(), file.getId()));
        return result;
    }

    public <T> void fill(long userId, FileBizType type, List<T> rows,
                         Function<T, Long> id, BiConsumer<T, String> setFileId) {
        var images = findAll(userId, type, rows.stream().map(id).toList());
        rows.forEach(row -> setFileId.accept(row, images.get(id.apply(row))));
    }

    private static void requireSupported(FileBizType type) {
        if (type != FileBizType.DEVICE && type != FileBizType.MOVIE
                && type != FileBizType.READ_RECORD && type != FileBizType.WARDROBE_ITEM) {
            throw new IllegalArgumentException("不支持的单图业务类型");
        }
    }
}
