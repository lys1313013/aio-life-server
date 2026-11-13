package top.aiolife.export.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import top.aiolife.core.id.IdCodec;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.resq.PageResp;
import top.aiolife.core.web.CurrentUserId;
import top.aiolife.export.convertor.ExportJobConvertor;
import top.aiolife.export.pojo.req.ExportAuthorizeReq;
import top.aiolife.export.pojo.req.ExportCreateReq;
import top.aiolife.export.pojo.vo.ExportJobVO;
import top.aiolife.export.service.ExportApplicationService;
import top.aiolife.export.service.ExportQuotaService;

import java.util.Map;

/**
 * 当前用户数据导出控制器。
 *
 * @author Lys
 * @date 2026/11/13
 */
@Tag(name = "数据导出")
@RestController
@RequestMapping("/me/data-exports")
@RequiredArgsConstructor
public class ExportController {
    private final ExportApplicationService jobs;

    @Operation(summary = "查询可导出的数据集")
    @GetMapping("/catalog")
    public ApiResponse<ExportApplicationService.Catalog> catalog(@CurrentUserId String userId) {
        return ApiResponse.success(jobs.catalog(IdCodec.decode(userId)));
    }

    @Operation(summary = "查询数据导出配额")
    @GetMapping("/quota")
    public ApiResponse<ExportQuotaService.Quota> quota(@CurrentUserId String userId) {
        return ApiResponse.success(jobs.quota(IdCodec.decode(userId)));
    }

    @Operation(summary = "验证数据导出凭据并签发授权")
    @PostMapping("/authorizations")
    public ApiResponse<Map<String, String>> authorize(@CurrentUserId String userId,
                                                      @Valid @RequestBody ExportAuthorizeReq req) {
        return ApiResponse.success(Map.of("grant", jobs.authorize(IdCodec.decode(userId), req)));
    }

    @Operation(summary = "创建数据导出任务")
    @PostMapping
    public ApiResponse<ExportJobVO> create(
            @CurrentUserId String userId,
            @Valid @RequestBody ExportCreateReq req,
            @RequestHeader("Idempotency-Key") String key,
            @RequestHeader("X-Export-Grant") String grant,
            HttpServletRequest request) {
        return ApiResponse.success(ExportJobConvertor.INSTANCE.toVO(
                jobs.create(IdCodec.decode(userId), req, key, grant, request.getRemoteAddr())));
    }

    @Operation(summary = "分页查询我的数据导出任务")
    @GetMapping
    public ApiResponse<PageResp<ExportJobVO>> history(
            @CurrentUserId String userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(
                jobs.history(IdCodec.decode(userId), page, pageSize)
                        .map(ExportJobConvertor.INSTANCE::toVO));
    }

    @Operation(summary = "查询数据导出任务详情")
    @GetMapping("/{id}")
    public ApiResponse<ExportJobVO> detail(@CurrentUserId String userId, @PathVariable String id) {
        return ApiResponse.success(ExportJobConvertor.INSTANCE.toVO(
                jobs.detail(IdCodec.decode(userId), IdCodec.decode(id))));
    }

    @Operation(summary = "取消数据导出任务")
    @PostMapping("/{id}/cancel")
    public ApiResponse<Void> cancel(@CurrentUserId String userId, @PathVariable String id) {
        jobs.cancel(IdCodec.decode(userId), IdCodec.decode(id));
        return ApiResponse.success();
    }

    @Operation(summary = "删除数据导出文件")
    @DeleteMapping("/{id}/artifact")
    public ApiResponse<Void> delete(@CurrentUserId String userId, @PathVariable String id) {
        jobs.revokeArtifact(IdCodec.decode(userId), IdCodec.decode(id));
        return ApiResponse.success();
    }

    @Operation(summary = "下载数据导出文件")
    @GetMapping("/{id}/download")
    public void download(
            @CurrentUserId String userId,
            @PathVariable String id,
            @RequestHeader(value = "X-Export-Grant", required = false) String grant,
            @RequestHeader(value = "Range", required = false) String range,
            HttpServletRequest request,
            HttpServletResponse response)
            throws Exception {
        jobs.download(IdCodec.decode(userId), IdCodec.decode(id), grant,
                range, request.getMethod(), response);
    }
}
