package top.aiolife.export.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import cn.dev33.satoken.annotation.SaCheckRole;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import top.aiolife.core.id.IdCodec;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.resq.PageResp;
import top.aiolife.core.web.CurrentUserId;
import top.aiolife.export.convertor.ExportJobConvertor;
import top.aiolife.export.service.ExportAuditService;

import java.time.LocalDateTime;

/**
 * 只读审计元数据，绝不提供他人文件下载和代导出入口。
 *
 * @author Lys
 * @date 2026/11/13
 */
@Tag(name = "数据导出审计")
@RestController
@RequestMapping("/system/data-export-audits")
@RequiredArgsConstructor
@SaCheckRole("admin")
public class ExportAuditController {
    private final ExportAuditService service;

    @Operation(summary = "管理员分页查询数据导出审计")
    @GetMapping
    public ApiResponse<PageResp<AuditView>> list(
            @CurrentUserId String actorId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String userId) {
        return ApiResponse.success(service.page(IdCodec.decode(actorId), page, pageSize,
                        IdCodec.decodeOrNull(userId))
                .map(ExportJobConvertor.INSTANCE::toAuditVO));
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
