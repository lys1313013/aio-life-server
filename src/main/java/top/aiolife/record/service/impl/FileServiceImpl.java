package top.aiolife.record.service.impl;

import top.aiolife.core.lock.StorageObjectLock;
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
import top.aiolife.core.util.FileContentPolicy;
import top.aiolife.record.enums.FileBizType;
import top.aiolife.record.mapper.IFileMapper;
import top.aiolife.record.pojo.entity.FileEntity;
import top.aiolife.record.pojo.vo.FileVO;
import top.aiolife.record.service.IFileService;
import top.aiolife.record.service.DoubanCoverUrlPolicy;

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
    private final StorageObjectLock objectLock;
    private final DoubanCoverUrlPolicy coverUrlPolicy;

    public FileServiceImpl(MinioUtil minioUtil, MinioConfig minioConfig, StorageObjectLock objectLock,
                           DoubanCoverUrlPolicy coverUrlPolicy) {
        this.minioUtil = minioUtil;
        this.minioConfig = minioConfig;
        this.objectLock = objectLock;
        this.coverUrlPolicy = coverUrlPolicy;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileVO upload(MultipartFile file, FileBizType bizType) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }

        boolean template = bizType == FileBizType.BANK_CARD_TEMPLATE_COVER;
        if (template) StpUtil.checkRole("admin");
        if (file.getSize() > FileContentPolicy.MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException("文件不能超过10MB");
        }
        byte[] uploadBytes;
        try (var input = file.getInputStream()) {
            uploadBytes = input.readNBytes(FileContentPolicy.MAX_IMAGE_BYTES + 1);
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("文件无法读取", e);
        }
        if (uploadBytes.length > FileContentPolicy.MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException("文件不能超过10MB");
        }
        var imageType = switch (bizType) {
            case AVATAR, WARDROBE_ITEM, MOVIE, READ_RECORD, BANK_CARD_COVER, BANK_CARD_TEMPLATE_COVER ->
                    FileContentPolicy.requireImage(uploadBytes);
            default -> FileContentPolicy.detectImage(uploadBytes);
        };
        String validatedContentType = imageType == null ? "application/octet-stream" : imageType.contentType();
        if (bizType == FileBizType.BANK_CARD_COVER || template) {
            validateBankCover(uploadBytes, validatedContentType);
        }
        long userId = StpUtil.getLoginIdAsLong();
        String bucketName = resolveBucketName();
        String objectName = buildObjectName(userId, bizType,
                imageType == null ? file.getOriginalFilename() : "image." + imageType.extension());

        objectLock.holdUntilTransactionCompletion(bucketName, objectName);
        long storedSize=uploadBytes.length;
        try {
            if (template) {
                // 无论源文件是 JPEG 还是 PNG，系统卡面统一解码重编码为 PNG。
                java.awt.image.BufferedImage image;
                try (var input=new ByteArrayInputStream(uploadBytes)) { image=javax.imageio.ImageIO.read(input); }
                if (image==null) throw new IllegalArgumentException("卡面图片无法读取");
                var output=new java.io.ByteArrayOutputStream();
                javax.imageio.ImageIO.write(image,"png",output);
                if (output.size()>5*1024*1024) throw new IllegalArgumentException("处理后的卡面不能超过5MB");
                byte[] bytes=output.toByteArray();
                storedSize=bytes.length;
                minioUtil.putObject(bucketName,objectName,new ByteArrayInputStream(bytes),bytes.length,"image/png");
                validatedContentType="image/png";
            } else minioUtil.putObject(bucketName, objectName, new ByteArrayInputStream(uploadBytes),
                    storedSize, validatedContentType);
            registerRollbackCleanup(bucketName, objectName);

            FileEntity fileEntity = new FileEntity();
            fileEntity.setFileName(objectName);
            fileEntity.setFileSize(storedSize);
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
        if (bizType==FileBizType.BANK_CARD_TEMPLATE_COVER) throw new IllegalArgumentException("公共卡面请使用图片上传");
        if (bizType != FileBizType.MOVIE && bizType != FileBizType.READ_RECORD) {
            throw new IllegalArgumentException("该业务请使用文件上传");
        }
        String validatedUrl = coverUrlPolicy.validate(imageUrl);
        long userId = StpUtil.getLoginIdAsLong();
        String bucketName = resolveBucketName();

        byte[] bodyBytes;
        String contentType;
        try (HttpResponse response = HttpRequest.get(validatedUrl)
                .header("Referer", "https://movie.douban.com/")
                .header("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1")
                .timeout(10000)
                // 禁止白名单 CDN 通过重定向转向其他主机。
                .setFollowRedirects(false)
                // 流式读取，避免默认 execute() 在限额校验前缓冲整个响应。
                .executeAsync()) {
            int status = response.getStatus();
            if (status != 200) {
                throw new IllegalStateException("下载封面图失败，HTTP " + status);
            }
            try (var input = response.bodyStream()) {
                bodyBytes = input.readNBytes(FileContentPolicy.MAX_IMAGE_BYTES + 1);
            } catch (java.io.IOException e) {
                throw new IllegalStateException("读取封面图失败", e);
            }
        }

        var imageType = FileContentPolicy.requireImage(bodyBytes);
        contentType = imageType.contentType();
        String extension = "." + imageType.extension();
        String objectName = buildObjectName(userId, bizType, "cover" + extension);
        objectLock.holdUntilTransactionCompletion(bucketName, objectName);

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

    private void validateBankCover(byte[] bytes, String contentType) {
        if (bytes.length > 5 * 1024 * 1024) throw new IllegalArgumentException("卡面图片不能超过5MB");
        if (!java.util.Set.of("image/png", "image/jpeg").contains(contentType)) {
            throw new IllegalArgumentException("卡面仅支持PNG或JPEG图片");
        }
    }

    private String resolveBucketName() {
        return StringUtils.hasText(minioConfig.getBucketName()) ? minioConfig.getBucketName() : "aiolife";
    }

    private String buildObjectName(long userId, FileBizType bizType, String originalFilename) {
        if (bizType==FileBizType.BANK_CARD_TEMPLATE_COVER)
            return "system/bank-card-covers/" + UUID.randomUUID() + ".png";
        String extension = StringUtils.getFilenameExtension(originalFilename);
        String suffix = StringUtils.hasText(extension) && extension.matches("[A-Za-z0-9]{1,10}")
                ? "." + extension.toLowerCase(Locale.ROOT)
                : "";
        return userId + "/" + bizType.getDirectory() + "/" + UUID.randomUUID() + suffix;
    }

    private void registerRollbackCleanup(String bucketName, String objectName) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            // 回滚清理必须先于对象锁释放，防止清理期间另一个操作进入。
            @Override public int getOrder() { return Integer.MAX_VALUE - 1; }
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
        if ("b_video_cover".equals(bizType))
            throw new IllegalArgumentException("视频封面只能通过视频导入绑定");
        if ("bank_card_cover".equals(bizType) || "bank_card_template_cover".equals(bizType))
            throw new IllegalArgumentException("请通过银行卡页面绑定卡面");
        if (this.count(new LambdaQueryWrapper<FileEntity>()
                .in(FileEntity::getId, fileIds).eq(FileEntity::getBizType, "b_video_cover")) > 0)
            throw new IllegalArgumentException("视频封面只能通过视频导入绑定");
        // 银行卡卡面只能通过银行卡事务绑定，不能被通用入口迁走。
        if (this.count(new LambdaQueryWrapper<FileEntity>()
                .in(FileEntity::getId, fileIds).in(FileEntity::getBizType, "bank_card_cover", "bank_card_template_cover")) > 0)
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
        if (FileBizType.AVATAR.getBizType().equals(entity.getBizType())) {
            vo.setFileUrl(minioUtil.getFilePreviewUrl(entity.getId()));
            return vo;
        }
        // 构造文件预览 URL
        if (entity.getStorageObjectId() != null) {
            vo.setFileUrl("/api/file/preview/" + entity.getId());
            return vo;
        }
        String bucketName = resolveBucketName();
        vo.setFileUrl(minioUtil.getPreviewUrl(bucketName, entity.getFileName()));
        return vo;
    }
}
