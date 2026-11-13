package top.aiolife.export.service;

import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import top.aiolife.core.exception.SecondaryLockRequiredException;
import top.aiolife.core.resq.PageResp;
import top.aiolife.export.config.ExportProperties;
import top.aiolife.export.dataset.ExportDatasetRegistry;
import top.aiolife.export.pojo.entity.ExportJobEntity;
import top.aiolife.export.pojo.req.ExportAuthorizeReq;
import top.aiolife.export.pojo.req.ExportCreateReq;
import top.aiolife.export.storage.ExportArtifactStore;

import java.util.ArrayList;
import java.util.List;

/**
 * 数据导出应用编排：统一处理开关、会话校验、限流和任务操作，Controller 只传用户 ID。
 *
 * @author Lys
 * @date 2026/11/13
 */
@Service
@RequiredArgsConstructor
public class ExportApplicationService {
    private final ExportProperties properties;
    private final ExportDatasetRegistry registry;
    private final ExportAccessGuard access;
    private final ExportQuotaService quota;
    private final ExportAuthorizationService authorization;
    private final ExportJobService jobs;
    private final ExportDownloadService downloads;
    private final ExportRateLimiter rate;
    private final ExportArtifactStore storage;

    /** 校验导出开关与本次会话身份。 */
    public void requireReady(long userId) {
        long sessionUser = access.requestUser();
        if (sessionUser != userId) throw ExportException.denied();
        if (!properties.isEnabled()) throw ExportException.unavailable();
    }

    public Catalog catalog(long userId) {
        requireReady(userId);
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
        return new Catalog(
                registry.version(),
                properties.getArtifactHours(),
                items,
                secondary ? "SECONDARY_PASSWORD" : "PASSWORD",
                List.of(
                        "不含登录凭据、密码库秘密、完整卡号和附件原件",
                        "不含已删除数据、历史停用模块和外部实时数据",
                        "不可用或锁定模块需处理后再选择"));
    }

    public ExportQuotaService.Quota quota(long userId) {
        requireReady(userId);
        return quota.current(userId);
    }

    public String authorize(long userId, ExportAuthorizeReq req) {
        requireReady(userId);
        return authorization.authorize(userId, req);
    }

    public ExportJobEntity create(long userId, ExportCreateReq req, String key, String grant, String clientIp) {
        requireReady(userId);
        rate.check("create-user", String.valueOf(userId), 6, 60);
        rate.check("create-ip", clientIp, 20, 60);
        return jobs.create(userId, req, key, grant);
    }

    public PageResp<ExportJobEntity> history(long userId, int page, int size) {
        requireReady(userId);
        return jobs.history(userId, page, size);
    }

    public ExportJobEntity detail(long userId, long id) {
        requireReady(userId);
        return jobs.owned(userId, id);
    }

    public void cancel(long userId, long id) {
        requireReady(userId);
        jobs.cancel(userId, id);
    }

    public void revokeArtifact(long userId, long id) {
        requireReady(userId);
        jobs.revokeArtifact(userId, id, "DELETED");
    }

    public void download(long userId, long id, String grant, String range, String method,
                         HttpServletResponse response) throws Exception {
        requireReady(userId);
        if ("HEAD".equals(method)) {
            downloads.check(userId, jobs.owned(userId, id));
            return;
        }
        if (range != null) throw new IllegalArgumentException("暂不支持分段下载");
        rate.check("download", String.valueOf(userId), 5, 60);
        try (var lease = downloads.acquire(userId)) {
            var transfer = downloads.begin(userId, id, grant);
            downloads.stream(transfer, storage, response);
        }
    }

    public record DatasetInfo(String key, String title, String state, String lockedMenu) {
    }

    public record Catalog(
            String version,
            int artifactHours,
            List<DatasetInfo> datasets,
            String verificationMethod,
            List<String> exclusions) {
    }
}
