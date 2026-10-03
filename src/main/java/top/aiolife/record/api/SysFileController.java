package top.aiolife.record.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.util.MinioUtil;
import top.aiolife.core.util.FileContentPolicy;
import top.aiolife.record.enums.FileBizType;
import top.aiolife.record.pojo.entity.FileEntity;
import top.aiolife.record.pojo.vo.FileVO;
import top.aiolife.record.service.FilePreviewGuard;
import top.aiolife.record.service.IFileService;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/file")
public class SysFileController {

    private final IFileService fileService;
    private final MinioUtil minioUtil;
    private final top.aiolife.config.MinioConfig minioConfig;
    private final FilePreviewGuard filePreviewGuard;
    private final top.aiolife.sso.service.SecondaryLockGuard secondaryLockGuard;

    /**
     * 统一文件上传入口。
     */
    @PostMapping("/upload")
    public ApiResponse<FileVO> upload(@RequestParam("file") MultipartFile file,
                                      @RequestParam("bizType") String bizType) {
        if ("bank_card_cover".equals(bizType)) secondaryLockGuard.checkMenus(
                cn.dev33.satoken.stp.StpUtil.getLoginIdAsLong(), "/finance/bank-cards");
        return ApiResponse.success(fileService.upload(file, FileBizType.fromBizType(bizType)));
    }

    @GetMapping("/preview/{id:[a-fA-F0-9]{32}}")
    @Operation(summary = "按文件 ID 预览文件")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "文件内容，Content-Type 取决于文件类型；不是 ApiResponse JSON",
            content = @Content(mediaType = "*/*", schema = @Schema(type = "string", format = "binary")))
    public void preview(@Parameter(description = "上传响应中的文件 ID", schema = @Schema(pattern = "^[a-fA-F0-9]{32}$"))
                        @PathVariable("id") String id, HttpServletResponse response) {
        handleFileRequest(id, response, false);
    }

    @GetMapping("/download/{id:[a-fA-F0-9]{32}}")
    @Operation(summary = "按文件 ID 下载文件")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "文件内容，Content-Disposition 为 attachment；不是 ApiResponse JSON",
            content = @Content(mediaType = "*/*", schema = @Schema(type = "string", format = "binary")))
    public void download(@Parameter(description = "上传响应中的文件 ID", schema = @Schema(pattern = "^[a-fA-F0-9]{32}$"))
                         @PathVariable("id") String id, HttpServletResponse response) {
        handleFileRequest(id, response, true);
    }

    private void handleFileRequest(String id, HttpServletResponse response, boolean isDownload) {
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

        if ("bank_card_cover".equals(fileEntity.getBizType()) || "bank_card_template_cover".equals(fileEntity.getBizType())) {
            response.setHeader("Cache-Control", "no-store");
            response.setHeader("X-Content-Type-Options", "nosniff");
        }
        // 从 MinIO 拿取文件
        String bucketName = StringUtils.hasText(minioConfig.getBucketName()) ? minioConfig.getBucketName() : "aiolife";
        String objectName = normalizeObjectName(fileEntity.getFileName(), bucketName);
        objectName = resolveObjectName(fileEntity, bucketName, objectName);

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

    private String normalizeObjectName(String objectName, String bucketName) {
        if (!StringUtils.hasText(objectName)) {
            return objectName;
        }
        int idx = objectName.indexOf("/" + bucketName + "/");
        if (idx != -1) {
            return objectName.substring(idx + bucketName.length() + 2);
        }
        return objectName;
    }

    private String resolveObjectName(FileEntity fileEntity, String bucketName, String objectName) {
        List<String> candidates = buildObjectCandidates(fileEntity, objectName);
        for (String candidate : candidates) {
            if (minioUtil.objectExists(bucketName, candidate)) {
                return candidate;
            }
        }
        return objectName;
    }

    private List<String> buildObjectCandidates(FileEntity fileEntity, String objectName) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        if (!StringUtils.hasText(objectName)) {
            return new ArrayList<>();
        }
        candidates.add(objectName);
        if (objectName.contains("/") || fileEntity.getCreateUser() == null || !StringUtils.hasText(fileEntity.getBizType())) {
            return new ArrayList<>(candidates);
        }
        String userPrefix = String.valueOf(fileEntity.getCreateUser());
        String bizType = fileEntity.getBizType();
        LinkedHashSet<String> bizDirs = new LinkedHashSet<>();
        bizDirs.add(bizType);
        bizDirs.add(bizType.replace('_', '-'));
        if (bizType.endsWith("_record")) {
            bizDirs.add(bizType.substring(0, bizType.length() - "_record".length()));
        }
        for (String bizDir : bizDirs) {
            if (StringUtils.hasText(bizDir)) {
                candidates.add(userPrefix + "/" + bizDir + "/" + objectName);
            }
        }
        return new ArrayList<>(candidates);
    }
}
