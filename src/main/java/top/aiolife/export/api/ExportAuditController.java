package top.aiolife.export.api;

import cn.dev33.satoken.annotation.SaCheckRole;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.resq.PageResp;
import top.aiolife.export.convertor.ExportJobConvertor;
import top.aiolife.export.mapper.ExportAuditMapper;
import top.aiolife.export.pojo.entity.ExportAuditEntity;
import top.aiolife.export.service.ExportAccessGuard;
import top.aiolife.sso.service.SecondaryLockGuard;

import java.time.LocalDateTime;

/** 只读审计元数据，绝不提供他人文件下载和代导出入口。 */
@RestController
@RequestMapping("/system/data-export-audits")
@RequiredArgsConstructor
@SaCheckRole("admin")
public class ExportAuditController {
    private final ExportAuditMapper mapper;
    private final ExportAccessGuard access;
    private final SecondaryLockGuard locks;

    @GetMapping
    public ApiResponse<PageResp<AuditView>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long userId) {
        long actorId = access.requestUser();
        locks.checkMenus(actorId, "/system/operation-log");
        if (page < 1 || pageSize < 1 || pageSize > 100)
            throw new IllegalArgumentException("分页参数无效");
        var result =
                mapper.selectPage(
                        new Page<>(page, pageSize),
                        new LambdaQueryWrapper<ExportAuditEntity>()
                                .eq(userId != null, ExportAuditEntity::getUserId, userId)
                                .orderByDesc(
                                        ExportAuditEntity::getEventTime, ExportAuditEntity::getId));
        return ApiResponse.success(
                new PageResp<>(
                        result.getRecords().stream()
                                .map(ExportJobConvertor.INSTANCE::toAuditVO)
                                .toList(),
                        result.getTotal()));
    }

    public record AuditView(
            String id,
            String userId,
            String jobId,
            String eventType,
            String resultCode,
            String clientIp,
            String traceId,
            String details,
            LocalDateTime eventTime) {}
}
