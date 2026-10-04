package top.aiolife.sso.service;

import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.aiolife.core.util.MinioUtil;
import top.aiolife.record.mapper.IFileMapper;
import top.aiolife.record.pojo.entity.FileEntity;

/** 用户头像仅绑定 file.id，URL 由当前服务配置生成，不读取历史 avatar 列。 */
@Service
@RequiredArgsConstructor
public class UserAvatarFileService {
    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/gif", "image/webp", "image/bmp");
    private final IFileMapper files;
    private final MinioUtil minio;

    /** 在用户保存事务中锁定文件，阻止绑定检查与文件删除之间的竞态。 */
    public void validateForBinding(Long userId, String fileId) {
        if (fileId == null) return;
        if (userId == null || !fileId.matches("[a-fA-F0-9]{32}"))
            throw new IllegalArgumentException("头像文件 ID 无效");
        if (!isAvatar(files.selectForAvatarBinding(fileId), userId))
            throw new IllegalArgumentException("头像文件不存在、不可用或不属于该用户");
    }

    public String publicUrl(Long userId, String fileId) {
        if (fileId == null || !fileId.matches("[a-fA-F0-9]{32}")) return null;
        var file = files.selectById(fileId);
        return isAvatar(file, userId) ? minio.getPublicImageUrl(fileId, file.getFileType()) : null;
    }

    private boolean isAvatar(FileEntity file, Long userId) {
        return file != null && Objects.equals(file.getIsDeleted(), 0)
                && userId != null && userId.equals(file.getCreateUser())
                && "avatar".equals(file.getBizType()) && Objects.equals(file.getIsPublic(), 1)
                && IMAGE_TYPES.contains(file.getFileType() == null ? "" : file.getFileType());
    }
}
