package top.aiolife.sso.api;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import top.aiolife.config.CbtiConfig;
import top.aiolife.config.MinioConfig;
import top.aiolife.core.util.MinioUtil;
import top.aiolife.record.pojo.entity.FileEntity;
import top.aiolife.record.service.FilePreviewGuard;
import top.aiolife.record.service.IFileService;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * 文件控制器（按 桶名/对象路径 直接访问的兼容接口）。
 *
 * 新生成的文件访问应走 {@code /file/preview/{id}}（SysFileController，按文件 ID 访问）。
 * 本接口保留用于兼容历史生成的 URL（FileVO.fileUrl、CBTI 图片），访问控制：
 * <ul>
 *   <li>仅允许访问配置的默认桶与 CBTI 桶，杜绝任意桶读取</li>
 *   <li>CBTI 公共图片前缀（images/cbti/characters/）匿名放行</li>
 *   <li>其余对象要求登录；存在文件记录时按 isPublic/属主/管理员校验</li>
 * </ul>
 *
 * @author Lys
 * @date 2025/4/5
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class FileController {

    private final MinioUtil minioUtil;
    private final MinioConfig minioConfig;
    private final CbtiConfig cbtiConfig;
    private final IFileService fileService;
    private final FilePreviewGuard filePreviewGuard;

    /**
     * 预览/下载文件
     *
     * @param fileName 文件名（包含桶名和路径）
     */
    @GetMapping("/file/preview/{*fileName}")
    public void preview(@PathVariable("fileName") String fileName, HttpServletResponse response) {
        // 去除前导 /
        if (fileName.startsWith("/")) {
            fileName = fileName.substring(1);
        }

        // 截取第一个 / 之前的内容作为桶名
        int splitIndex = fileName.indexOf("/");
        if (splitIndex == -1) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        String bucketName = fileName.substring(0, splitIndex);
        String objectName = fileName.substring(splitIndex + 1);

        if (!StringUtils.hasText(bucketName) || !StringUtils.hasText(objectName)) {
            return;
        }

        // 仅允许访问配置的桶，防止任意 bucket 读取
        if (!isAllowedBucket(bucketName)) {
            log.warn("拒绝访问未配置的桶: bucket={}", bucketName);
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        // CBTI 公共图片（人格形象等公开资源）匿名放行
        if (!isPublicObject(objectName)) {
            Long userId = filePreviewGuard.resolveLoginUserId();
            if (userId == null) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
            // 存在文件记录时按公开性/属主校验；历史遗留无记录对象登录即可读
            FileEntity fileEntity = findFileRecord(objectName);
            if (fileEntity != null) {
                FilePreviewGuard.AccessDecision decision = filePreviewGuard.check(fileEntity, userId);
                if (decision == FilePreviewGuard.AccessDecision.UNAUTHORIZED) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    return;
                }
                if (decision == FilePreviewGuard.AccessDecision.FORBIDDEN) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    return;
                }
            }
        }

        try (InputStream inputStream = minioUtil.getFile(bucketName, objectName);
             OutputStream outputStream = response.getOutputStream()) {

            // 根据文件后缀名动态设置Content-Type
            String contentType = "application/octet-stream"; // 默认类型
            if (objectName.toLowerCase().endsWith(".jpg") || objectName.toLowerCase().endsWith(".jpeg")) {
                contentType = "image/jpeg";
            } else if (objectName.toLowerCase().endsWith(".png")) {
                contentType = "image/png";
            } else if (objectName.toLowerCase().endsWith(".gif")) {
                contentType = "image/gif";
            } else if (objectName.toLowerCase().endsWith(".bmp")) {
                contentType = "image/bmp";
            } else if (objectName.toLowerCase().endsWith(".webp")) {
                contentType = "image/webp";
            }
            response.setContentType(contentType);

            byte[] buffer = new byte[1024];
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, len);
            }
            outputStream.flush();
        } catch (Exception e) {
            log.error("获取文件失败: bucket={}, objectName={}", bucketName, objectName, e);
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    /**
     * 是否为允许访问的桶：默认桶或 CBTI 配置桶
     */
    private boolean isAllowedBucket(String bucketName) {
        String defaultBucket = StringUtils.hasText(minioConfig.getBucketName()) ? minioConfig.getBucketName() : "aiolife";
        if (defaultBucket.equals(bucketName)) {
            return true;
        }
        return StringUtils.hasText(cbtiConfig.getBucketName()) && cbtiConfig.getBucketName().equals(bucketName);
    }

    /**
     * 是否为可匿名访问的公共对象（CBTI 人格形象等公开资源目录）
     */
    private boolean isPublicObject(String objectName) {
        String publicPrefix = StringUtils.hasText(cbtiConfig.getObjectPrefix())
                ? cbtiConfig.getObjectPrefix() : "images/cbti/characters/";
        if (publicPrefix.startsWith("/")) {
            publicPrefix = publicPrefix.substring(1);
        }
        return objectName.startsWith(publicPrefix);
    }

    /**
     * 按对象名查找文件记录（旧式 URL 的 objectName 即 file 表 file_name）
     */
    private FileEntity findFileRecord(String objectName) {
        return fileService.getOne(new LambdaQueryWrapper<FileEntity>()
                .eq(FileEntity::getFileName, objectName)
                .eq(FileEntity::getIsDeleted, 0)
                .last("LIMIT 1"), false);
    }
}
