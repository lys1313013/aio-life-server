package top.aiolife.sso.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.aiolife.core.util.MinioUtil;
import top.aiolife.record.mapper.IFileMapper;
import top.aiolife.record.pojo.entity.FileEntity;

/** 用户头像保存为 file.biz_type='avatar' 且 biz_id=用户ID 的单条关联，不在用户表保存文件引用。 */
@Service
@RequiredArgsConstructor
public class UserAvatarFileService {
    private static final String BIZ_TYPE = "avatar";
    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/gif", "image/webp", "image/bmp");
    private final IFileMapper files;
    private final MinioUtil minio;

    /** 当前头像；URL 由当前服务配置生成，不读取历史头像列。 */
    public Avatar current(long userId) {
        FileEntity file = files.selectOne(new LambdaQueryWrapper<FileEntity>()
                .eq(FileEntity::getBizType, BIZ_TYPE)
                .eq(FileEntity::getBizId, userId)
                .eq(FileEntity::getCreateUser, userId));
        if (!isAvatar(file, userId)) return new Avatar(null, null);
        return new Avatar(file.getId(), minio.getPublicImageUrl(file.getId(), file.getFileType()));
    }

    /** null 或空白清除当前头像；替换只解除旧关联，不删除存储对象。 */
    @Transactional(rollbackFor = Exception.class)
    public void replace(long userId, String fileId) {
        String selected = fileId == null ? null : fileId;
        if (selected != null) validateForBinding(userId, selected);
        files.update(null, new LambdaUpdateWrapper<FileEntity>()
                .eq(FileEntity::getBizType, BIZ_TYPE)
                .eq(FileEntity::getBizId, userId)
                .eq(FileEntity::getCreateUser, userId)
                .ne(selected != null, FileEntity::getId, selected)
                .set(FileEntity::getBizId, null)
                .set(FileEntity::getUpdateUser, userId).set(FileEntity::getUpdateTime, LocalDateTime.now()));
        if (selected != null) {
            files.update(null, new LambdaUpdateWrapper<FileEntity>()
                    .eq(FileEntity::getId, selected).eq(FileEntity::getCreateUser, userId)
                    .set(FileEntity::getBizId, userId)
                    .set(FileEntity::getUpdateUser, userId).set(FileEntity::getUpdateTime, LocalDateTime.now()));
        }
    }

    /** 在用户保存事务中锁定文件，阻止绑定检查与文件删除之间的竞态。 */
    public void validateForBinding(Long userId, String fileId) {
        if (fileId == null) return;
        if (userId == null || !fileId.matches("[a-fA-F0-9]{32}"))
            throw new IllegalArgumentException("头像文件 ID 无效");
        FileEntity file = files.selectForAvatarBinding(fileId);
        if (!isAvatar(file, userId)) throw new IllegalArgumentException("头像文件不存在、不可用或不属于该用户");
        if (file.getBizId() != null && !Objects.equals(file.getBizId(), userId))
            throw new IllegalArgumentException("头像文件已绑定其他用户");
    }

    private boolean isAvatar(FileEntity file, Long userId) {
        return file != null && Objects.equals(file.getIsDeleted(), 0)
                && userId != null && userId.equals(file.getCreateUser())
                && BIZ_TYPE.equals(file.getBizType()) && Objects.equals(file.getIsPublic(), 1)
                && IMAGE_TYPES.contains(file.getFileType() == null ? "" : file.getFileType());
    }

    public record Avatar(String fileId, String url) {}
}
