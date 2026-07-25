package top.aiolife.record.api;

import cn.dev33.satoken.stp.StpUtil;
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
import top.aiolife.record.enums.FileBizType;
import top.aiolife.record.pojo.entity.FileEntity;
import top.aiolife.record.pojo.vo.FileVO;
import top.aiolife.record.service.IFileService;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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

    /**
     * 统一文件上传入口。
     */
    @PostMapping("/upload")
    public ApiResponse<FileVO> upload(@RequestParam("file") MultipartFile file,
                                      @RequestParam("bizType") String bizType) {
        return ApiResponse.success(fileService.upload(file, FileBizType.fromBizType(bizType)));
    }

    @GetMapping("/preview/{id:[a-fA-F0-9]{32}}")
    public void preview(@PathVariable("id") String id, HttpServletResponse response) {
        handleFileRequest(id, response, false);
    }

    @GetMapping("/download/{id:[a-fA-F0-9]{32}}")
    public void download(@PathVariable("id") String id, HttpServletResponse response) {
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

        // 权限校验
        if (fileEntity.getIsPublic() != null && fileEntity.getIsPublic() == 0) {
            long userId = -1;
            List<String> roles = null;

            if (StpUtil.isLogin()) {
                userId = StpUtil.getLoginIdAsLong();
                roles = StpUtil.getRoleList();
            } else {
                // 处理 img 标签通过 Cookie 请求时，token 没有 Bearer 前缀导致 StpUtil.isLogin() 为 false 的情况
                String tokenName = StpUtil.getTokenName();
                String token = cn.dev33.satoken.context.SaHolder.getRequest().getCookieValue(tokenName);
                if (token == null) {
                    token = cn.dev33.satoken.context.SaHolder.getRequest().getParam(tokenName);
                }
                if (token != null) {
                    if (token.startsWith("Bearer ")) {
                        token = token.substring(7);
                    }
                    Object loginIdObj = StpUtil.getLoginIdByToken(token);
                    if (loginIdObj != null) {
                        userId = Long.parseLong(loginIdObj.toString());
                        roles = StpUtil.getRoleList(loginIdObj);
                    }
                }
            }

            if (userId == -1) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }

            boolean isAdmin = roles != null && roles.contains("admin");
            if (!isAdmin && fileEntity.getCreateUser() != null && !fileEntity.getCreateUser().equals(userId)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                return;
            }
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

        try (InputStream inputStream = minioUtil.getFile(bucketName, objectName);
             OutputStream outputStream = response.getOutputStream()) {

            String contentType = fileEntity.getFileType();
            if (!StringUtils.hasText(contentType)) {
                contentType = "application/octet-stream";
                if (objectName.toLowerCase().endsWith(".jpg") || objectName.toLowerCase().endsWith(".jpeg")) {
                    contentType = "image/jpeg";
                } else if (objectName.toLowerCase().endsWith(".png")) {
                    contentType = "image/png";
                } else if (objectName.toLowerCase().endsWith(".gif")) {
                    contentType = "image/gif";
                } else if (objectName.toLowerCase().endsWith(".webp")) {
                    contentType = "image/webp";
                }
            }
            response.setContentType(contentType);

            if (isDownload) {
                String fileName = fileEntity.getFileName();
                if (!StringUtils.hasText(fileName)) {
                    fileName = objectName.substring(objectName.lastIndexOf("/") + 1);
                }
                response.setHeader("Content-Disposition", "attachment; filename=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8));
            }

            byte[] buffer = new byte[1024];
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, len);
            }
            outputStream.flush();
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
