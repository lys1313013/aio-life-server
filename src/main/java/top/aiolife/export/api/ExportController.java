package top.aiolife.export.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import top.aiolife.core.exception.SecondaryLockRequiredException;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.resq.PageResp;
import top.aiolife.export.config.ExportProperties;
import top.aiolife.export.convertor.ExportJobConvertor;
import top.aiolife.export.dataset.ExportDatasetRegistry;
import top.aiolife.export.pojo.req.ExportAuthorizeReq;
import top.aiolife.export.pojo.req.ExportCreateReq;
import top.aiolife.export.pojo.vo.ExportJobVO;
import top.aiolife.export.service.ExportAccessGuard;
import top.aiolife.export.service.ExportAuthorizationService;
import top.aiolife.export.service.ExportDownloadService;
import top.aiolife.export.service.ExportException;
import top.aiolife.export.service.ExportJobService;
import top.aiolife.export.service.ExportQuotaService;
import top.aiolife.export.service.ExportRateLimiter;
import top.aiolife.export.storage.ExportArtifactStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/me/data-exports")
@RequiredArgsConstructor
public class ExportController {
    private final ExportAccessGuard access;
    private final ExportProperties properties;
    private final ExportDatasetRegistry registry;
    private final ExportAuthorizationService authorization;
    private final ExportQuotaService quota;
    private final ExportJobService jobs;
    private final ExportDownloadService downloads;
    private final ExportRateLimiter rate;
    private final ExportArtifactStore storage;

    private long user() {
        long userId = access.requestUser();
        if (!properties.isEnabled()) throw ExportException.unavailable();
        return userId;
    }

    @GetMapping("/catalog")
    public ApiResponse<Catalog> catalog() {
        long userId = user();
        List<DatasetInfo> items = new ArrayList<>();
        for (var dataset : registry.all()) {
            String state = "AVAILABLE";
            String menu = null;
            try {
                access.check(userId, List.of(dataset), true);
            } catch (SecondaryLockRequiredException locked) {
                state = "LOCKED";
                menu = locked.getMenuPath();
            } catch (ExportException forbidden) {
                state = "UNAVAILABLE";
            }
            items.add(new DatasetInfo(dataset.key(), dataset.title(), state, menu));
        }
        boolean secondary = StringUtils.hasText(access.user(userId).getSecondaryPassword());
        return ApiResponse.success(
                new Catalog(
                        registry.version(),
                        properties.getArtifactHours(),
                        items,
                        secondary ? "SECONDARY_PASSWORD" : "PASSWORD",
                        List.of(
                                "不含登录凭据、密码库秘密、完整卡号和附件原件",
                                "不含已删除数据、历史停用模块和外部实时数据",
                                "不可用或锁定模块需处理后再选择")));
    }

    @GetMapping("/quota")
    public ApiResponse<ExportQuotaService.Quota> quota() {
        return ApiResponse.success(quota.current(user()));
    }

    @PostMapping("/authorizations")
    public ApiResponse<Map<String, String>> authorize(@Valid @RequestBody ExportAuthorizeReq req) {
        return ApiResponse.success(Map.of("grant", authorization.authorize(user(), req)));
    }

    @PostMapping
    public ApiResponse<ExportJobVO> create(
            @Valid @RequestBody ExportCreateReq req,
            @RequestHeader("Idempotency-Key") String key,
            @RequestHeader("X-Export-Grant") String grant,
            HttpServletRequest request) {
        long userId = user();
        rate.check("create-user", String.valueOf(userId), 6, 60);
        rate.check("create-ip", request.getRemoteAddr(), 20, 60);
        return ApiResponse.success(
                ExportJobConvertor.INSTANCE.toVO(jobs.create(userId, req, key, grant)));
    }

    @GetMapping
    public ApiResponse<PageResp<ExportJobVO>> history(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(
                jobs.history(user(), page, pageSize).map(ExportJobConvertor.INSTANCE::toVO));
    }

    @GetMapping("/{id}")
    public ApiResponse<ExportJobVO> detail(@PathVariable long id) {
        return ApiResponse.success(ExportJobConvertor.INSTANCE.toVO(jobs.owned(user(), id)));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<Void> cancel(@PathVariable long id) {
        jobs.cancel(user(), id);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}/artifact")
    public ApiResponse<Void> delete(@PathVariable long id) {
        jobs.revokeArtifact(user(), id, "DELETED");
        return ApiResponse.success();
    }

    @GetMapping("/{id}/download")
    public void download(
            @PathVariable long id,
            @RequestHeader(value = "X-Export-Grant", required = false) String grant,
            HttpServletRequest request,
            HttpServletResponse response)
            throws Exception {
        long userId = user();
        if ("HEAD".equals(request.getMethod())) {
            downloads.check(userId, jobs.owned(userId, id));
            return;
        }
        if (request.getHeader("Range") != null) throw new IllegalArgumentException("暂不支持分段下载");
        rate.check("download", String.valueOf(userId), 5, 60);
        try (var lease = downloads.acquire(userId)) {
            var transfer = downloads.begin(userId, id, grant);
            downloads.stream(transfer, storage, response);
        }
    }

    public record DatasetInfo(String key, String title, String state, String lockedMenu) {}

    public record Catalog(
            String version,
            int artifactHours,
            List<DatasetInfo> datasets,
            String verificationMethod,
            List<String> exclusions) {}
}
