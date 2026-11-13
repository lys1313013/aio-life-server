package top.aiolife.record.service;

import jakarta.servlet.http.HttpServletResponse;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import top.aiolife.config.MinioConfig;
import top.aiolife.core.util.FileContentPolicy;
import top.aiolife.core.util.MinioUtil;
import top.aiolife.record.enums.FileBizType;
import top.aiolife.record.pojo.entity.FileEntity;
import top.aiolife.record.pojo.vo.FileVO;
import top.aiolife.sso.service.SecondaryLockGuard;
import top.aiolife.system.mapper.StorageObjectMapper;

/**
 * 文件上传授权与二进制流输出服务。
 *
 * @author Lys
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileAccessService {

    private final StorageObjectMapper storageObjects;
    private final IFileService fileService;
    private final MinioUtil minioUtil;
    private final MinioConfig minioConfig;
    private final FilePreviewGuard filePreviewGuard;
    private final SecondaryLockGuard secondaryLockGuard;

    /**
     * 统一文件上传入口，银行卡封面需先通过二级锁校验。
     */
    public FileVO upload(MultipartFile file, String bizType, long userId) {
        if ("bank_card_cover".equals(bizType)) {
            secondaryLockGuard.checkMenus(userId, "/finance/bank-cards");
        }
        return fileService.upload(file, FileBizType.fromBizType(bizType));
    }

    /**
     * 将文件内容写入响应流。文件不存在、未登录或无权限时只设置 HTTP 状态码。
     */
    public void write(String id, boolean isDownload, HttpServletResponse response) {
        log.info("SysFileController handleFileRequest start, id={}, isDownload={}", id, isDownload);
        FileEntity fileEntity = fileService.getById(id);
        if (fileEntity == null) {
            log.warn("SysFileController fileEntity is null, id={}", id);
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        // 权限校验（未登录 401，非属主/管理员 403）
        Long userId = filePreviewGuard.resolveLoginUserId();
        FilePreviewGuard.AccessDecision decision = filePreviewGuard.check(fileEntity, userId);
        if (decision == FilePreviewGuard.AccessDecision.UNAUTHORIZED) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        if (decision == FilePreviewGuard.AccessDecision.FORBIDDEN) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        if ("bank_card_cover".equals(fileEntity.getBizType())
                || "bank_card_template_cover".equals(fileEntity.getBizType())) {
            response.setHeader("Cache-Control", "no-store");
            response.setHeader("X-Content-Type-Options", "nosniff");
        }
        if (fileEntity.getStorageObjectId() != null) {
            var object = storageObjects.selectById(fileEntity.getStorageObjectId());
            if (object == null || !"system".equals(object.getBucket())
                    || !object.getObjectKey().matches("bvedio/[a-f0-9]{64}\\.(jpg|png|gif|webp|bmp)")) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            response.setHeader("Cache-Control", "no-store");
            try (var input = minioUtil.getFile(object.getBucket(), object.getObjectKey())) {
                FileContentPolicy.writeResponse(input, response, object.getObjectKey(), isDownload);
            } catch (Exception e) {
                log.warn("内部图片读取失败 fileId={}", id);
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            }
            return;
        }
        // 从 MinIO 拿取文件
        String bucketName = StringUtils.hasText(minioConfig.getBucketName())
                ? minioConfig.getBucketName() : "aiolife";
        String objectName = fileEntity.getFileName();

        if (!StringUtils.hasText(objectName)) {
            log.error("文件名或路径不存在: id={}", id);
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        try (InputStream inputStream = minioUtil.getFile(bucketName, objectName)) {
            FileContentPolicy.writeResponse(inputStream, response, objectName, isDownload);
        } catch (Exception e) {
            log.error("获取文件失败", e);
            log.error("获取文件失败: id={}, bucket={}, objectName={}", id, bucketName, objectName);
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }
    }
}
