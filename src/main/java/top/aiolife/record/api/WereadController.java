package top.aiolife.record.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import top.aiolife.core.id.IdCodec;
import top.aiolife.core.query.QueryParams;
import top.aiolife.core.web.CurrentUserId;
import top.aiolife.record.pojo.query.WereadRecentQuery;
import org.springframework.web.bind.annotation.*;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.record.pojo.req.WereadConnectionReq;
import top.aiolife.record.pojo.vo.WereadConnectionVO;
import top.aiolife.record.pojo.vo.WereadBookLinkVO;
import top.aiolife.record.pojo.vo.WereadRecentVO;
import top.aiolife.record.service.IWereadService;

/** 微信读书独立入口，所有数据使用当前登录用户的连接。 */
@Tag(name = "微信读书")
@RestController
@SaCheckLogin
@RequiredArgsConstructor
@RequestMapping("/weread")
public class WereadController {
    private final IWereadService service;

    @Operation(summary = "分页查询微信读书最近阅读")
    @GetMapping("/recent")
    public ApiResponse<WereadRecentVO> recent(@CurrentUserId String userId,
            @Valid @QueryParams WereadRecentQuery query) {
        return ApiResponse.success(service.recent(IdCodec.decode(userId), query.getCursor(), query.getPageSize()));
    }

    @Operation(summary = "查询微信读书连接状态")
    @GetMapping("/connection")
    public ApiResponse<WereadConnectionVO> connection(@CurrentUserId String userId) {
        return ApiResponse.success(service.connection(IdCodec.decode(userId)));
    }

    @Operation(summary = "连接微信读书账号")
    @PostMapping("/connection")
    public ApiResponse<WereadConnectionVO> connect(@CurrentUserId String userId,
            @Valid @RequestBody WereadConnectionReq request) {
        return ApiResponse.success(service.connect(IdCodec.decode(userId), request.getApiKey()));
    }

    @Operation(summary = "断开微信读书连接")
    @PostMapping("/disconnect")
    public ApiResponse<Void> disconnect(@CurrentUserId String userId) {
        service.disconnect(IdCodec.decode(userId));
        return ApiResponse.success();
    }

    @Operation(summary = "同步微信读书统计")
    @PostMapping("/sync")
    public ApiResponse<JsonNode> sync(@CurrentUserId String userId,
            @Parameter(description = "统计周期：weekly、monthly、annually、overall") @RequestParam(defaultValue = "annually") String mode,
            @Parameter(description = "基准 Unix 时间，秒；默认 0，overall 固定使用 0") @RequestParam(defaultValue = "0") long baseTime) {
        return ApiResponse.success(service.sync(IdCodec.decode(userId), mode, baseTime));
    }

    @Operation(summary = "查询微信读书统计")
    @GetMapping("/stats")
    public ApiResponse<JsonNode> stats(@CurrentUserId String userId,
            @Parameter(description = "统计周期：weekly、monthly、annually、overall") @RequestParam(defaultValue = "annually") String mode,
            @Parameter(description = "基准 Unix 时间，秒；默认 0，overall 固定使用 0") @RequestParam(defaultValue = "0") long baseTime) {
        return ApiResponse.success(service.stats(IdCodec.decode(userId), mode, baseTime));
    }

    @Operation(summary = "查询微信读书笔记")
    @GetMapping("/notes")
    public ApiResponse<JsonNode> notes(@CurrentUserId String userId,
            @Parameter(description = "微信读书书籍 ID") @RequestParam String bookId) {
        return ApiResponse.success(service.notes(IdCodec.decode(userId), bookId));
    }

    @Operation(summary = "查询微信读书书籍链接")
    @GetMapping("/book-link")
    public ApiResponse<WereadBookLinkVO> bookLink(@CurrentUserId String userId,
            @Parameter(description = "微信读书书籍 ID") @RequestParam String bookId) {
        return ApiResponse.success(service.bookLink(IdCodec.decode(userId), bookId));
    }

    @Operation(summary = "查询微信读书阅读进度")
    @GetMapping("/progress")
    public ApiResponse<JsonNode> progress(@CurrentUserId String userId,
            @Parameter(description = "微信读书书籍 ID") @RequestParam String bookId) {
        return ApiResponse.success(service.progress(IdCodec.decode(userId), bookId));
    }
}
