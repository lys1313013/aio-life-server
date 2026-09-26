package top.aiolife.record.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import top.aiolife.config.MinioConfig;
import top.aiolife.core.util.MinioUtil;
import top.aiolife.record.enums.FileBizType;
import top.aiolife.record.mapper.IFileMapper;
import top.aiolife.record.pojo.entity.FileEntity;
import top.aiolife.record.pojo.vo.FileVO;
import top.aiolife.record.service.IFileService;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;

import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class FileServiceImpl extends ServiceImpl<IFileMapper, FileEntity> implements IFileService {

    private final MinioUtil minioUtil;
    private final MinioConfig minioConfig;

    public FileServiceImpl(MinioUtil minioUtil, MinioConfig minioConfig) {
        this.minioUtil = minioUtil;
        this.minioConfig = minioConfig;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileVO upload(MultipartFile file, FileBizType bizType) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }

        String validatedContentType = bizType == FileBizType.BANK_CARD_COVER ? validateBankCover(file) : file.getContentType();
        long userId = StpUtil.getLoginIdAsLong();
        String bucketName = resolveBucketName();
        String objectName = buildObjectName(userId, bizType, file.getOriginalFilename());

        try {
            minioUtil.uploadFile(bucketName, file, objectName);
            registerRollbackCleanup(bucketName, objectName);

            FileEntity fileEntity = new FileEntity();
            fileEntity.setFileName(objectName);
            fileEntity.setFileSize(file.getSize());
            fileEntity.setFileType(validatedContentType);
            fileEntity.setBizType(bizType.getBizType());
            fileEntity.setIsPublic(bizType.getVisibility().getValue());
            fileEntity.setHashValue("");
            fileEntity.fillCreateCommonField(userId);

            if (!this.save(fileEntity)) {
                throw new IllegalStateException("文件记录保存失败");
            }
            return toVO(fileEntity);
        } catch (Exception e) {
            throw new IllegalStateException("上传失败: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileVO uploadFromUrl(String imageUrl, FileBizType bizType) {
        long userId = StpUtil.getLoginIdAsLong();
        String bucketName = resolveBucketName();

        HttpResponse response = HttpRequest.get(imageUrl)
                .header("Referer", "https://movie.douban.com/")
                .header("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1")
                .timeout(10000)
                .execute();

        int status = response.getStatus();
        if (status != 200) {
            throw new IllegalStateException("下载封面图失败，HTTP " + status + ": " + imageUrl);
        }

        byte[] bodyBytes = response.bodyBytes();
        String contentType = response.header("Content-Type");
        if (StrUtil.isBlank(contentType) || !contentType.startsWith("image/")) {
            throw new IllegalStateException("下载的不是图片，Content-Type=" + contentType + ", bodySize=" + bodyBytes.length);
        }

        String extension = extractExtension(imageUrl, contentType);
        String objectName = buildObjectName(userId, bizType, "cover" + extension);

        try {
            minioUtil.putObject(bucketName, objectName, new ByteArrayInputStream(bodyBytes), bodyBytes.length, contentType);
            registerRollbackCleanup(bucketName, objectName);

            FileEntity fileEntity = new FileEntity();
            fileEntity.setFileName(objectName);
            fileEntity.setFileSize((long) bodyBytes.length);
            fileEntity.setFileType(contentType);
            fileEntity.setBizType(bizType.getBizType());
            fileEntity.setIsPublic(bizType.getVisibility().getValue());
            fileEntity.setHashValue("");
            fileEntity.fillCreateCommonField(userId);

            if (!this.save(fileEntity)) {
                throw new IllegalStateException("文件记录保存失败");
            }
            return toVO(fileEntity);
        } catch (Exception e) {
            throw new IllegalStateException("URL 文件上传失败: " + e.getMessage(), e);
        }
    }

    private String validateBankCover(MultipartFile file) {
        if (file.getSize() > 5 * 1024 * 1024) throw new IllegalArgumentException("卡面图片不能超过5MB");
        try (var input = javax.imageio.ImageIO.createImageInputStream(file.getInputStream())) {
            var readers = javax.imageio.ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalArgumentException("卡面仅支持PNG或JPEG图片");
            var reader = readers.next();
            try {
                reader.setInput(input);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!java.util.Set.of("png", "jpeg", "jpg").contains(format)
                        || (long) reader.getWidth(0) * reader.getHeight(0) > 16_000_000)
                    throw new IllegalArgumentException("卡面须为不超过1600万像素的PNG或JPEG图片");
                return "png".equals(format) ? "image/png" : "image/jpeg";
            } finally { reader.dispose(); }
        } catch (java.io.IOException e) { throw new IllegalArgumentException("卡面图片无法读取"); }
    }

    private String extractExtension(String imageUrl, String contentType) {
        String path = imageUrl.contains("?") ? imageUrl.substring(0, imageUrl.indexOf("?")) : imageUrl;
        String ext = StrUtil.subAfter(path, ".", true);
        if (StrUtil.isNotBlank(ext) && ext.matches("[A-Za-z0-9]{1,10}")) {
            return "." + ext.toLowerCase(Locale.ROOT);
        }
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }

    private String resolveBucketName() {
        return StringUtils.hasText(minioConfig.getBucketName()) ? minioConfig.getBucketName() : "aiolife";
    }

    private String buildObjectName(long userId, FileBizType bizType, String originalFilename) {
        String extension = StringUtils.getFilenameExtension(originalFilename);
        String suffix = StringUtils.hasText(extension) && extension.matches("[A-Za-z0-9]{1,10}")
                ? "." + extension.toLowerCase(Locale.ROOT)
                : "";
        return userId + "/" + bizType.getDirectory() + "/" + UUID.randomUUID() + suffix;
    }

    private void registerRollbackCleanup(String bucketName, String objectName) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_ROLLED_BACK) {
                    return;
                }
                try {
                    minioUtil.removeObject(bucketName, objectName);
                } catch (Exception cleanupException) {
                    log.error("回滚后清理 MinIO 文件失败: bucket={}, objectName={}", bucketName, objectName, cleanupException);
                }
            }
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindBizId(List<String> fileIds, String bizType, Long bizId) {
        if (CollectionUtils.isEmpty(fileIds)) {
            return;
        }
        // 银行卡卡面只能通过银行卡事务绑定，不能被通用入口迁走。
        if ("bank_card_cover".equals(bizType) || this.count(new LambdaQueryWrapper<FileEntity>()
                .in(FileEntity::getId, fileIds).eq(FileEntity::getBizType, "bank_card_cover")) > 0)
            throw new IllegalArgumentException("请通过银行卡页面绑定卡面");
        // 只允许绑定当前用户自己上传的文件
        long userId = StpUtil.getLoginIdAsLong();
        LambdaUpdateWrapper<FileEntity> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.in(FileEntity::getId, fileIds)
                .eq(FileEntity::getCreateUser, userId)
                .set(FileEntity::getBizId, bizId)
                .set(FileEntity::getBizType, bizType);
        this.update(updateWrapper);
    }

    @Override
    public List<FileVO> getByBiz(String bizType, Long bizId) {
        LambdaQueryWrapper<FileEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(FileEntity::getBizType, bizType)
                .eq(FileEntity::getBizId, bizId)
                .eq(FileEntity::getIsDeleted, 0);
        List<FileEntity> list = this.list(queryWrapper);
        if (CollectionUtils.isEmpty(list)) {
            return Collections.emptyList();
        }
        return list.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public FileVO toVO(FileEntity entity) {
        if (entity == null) {
            return null;
        }
        FileVO vo = new FileVO();
        BeanUtils.copyProperties(entity, vo);
        vo.setId(entity.getId());
        // 构造文件预览 URL
        String bucketName = resolveBucketName();
        vo.setFileUrl(minioUtil.getPreviewUrl(bucketName, entity.getFileName()));
        return vo;
    }
}
