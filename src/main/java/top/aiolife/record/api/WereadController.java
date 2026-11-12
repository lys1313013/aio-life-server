package top.aiolife.record.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import top.aiolife.core.query.QueryParams;
import top.aiolife.record.pojo.query.WereadRecentQuery;
import org.springframework.web.bind.annotation.*;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.record.pojo.req.WereadConnectionReq;
import top.aiolife.record.pojo.vo.WereadConnectionVO;
import top.aiolife.record.pojo.vo.WereadBookLinkVO;
import top.aiolife.record.pojo.vo.WereadRecentVO;
import top.aiolife.record.service.IWereadService;

/** 微信读书独立入口，所有数据使用当前登录用户的连接。 */
@RestController
@SaCheckLogin
@RequiredArgsConstructor
@RequestMapping("/weread")
public class WereadController {
    private final IWereadService service;

    @Operation(summary = "分页查询微信读书最近阅读")
    @GetMapping("/recent")
    public ApiResponse<WereadRecentVO> recent(@Valid @QueryParams WereadRecentQuery query) {
        return ApiResponse.success(service.recent(query.getCursor(), query.getPageSize()));
    }

    @Operation(summary = "查询微信读书连接状态")
    @GetMapping("/connection")
    public ApiResponse<WereadConnectionVO> connection() {
        return ApiResponse.success(service.connection());
    }

    @Operation(summary = "连接微信读书账号")
    @PostMapping("/connection")
    public ApiResponse<WereadConnectionVO> connect(@RequestBody WereadConnectionReq request) {
        return ApiResponse.success(service.connect(request.getApiKey()));
    }

    @Operation(summary = "断开微信读书连接")
    @PostMapping("/disconnect")
    public ApiResponse<Void> disconnect() {
        service.disconnect();
        return ApiResponse.success();
    }

    @Operation(summary = "同步微信读书统计")
    @PostMapping("/sync")
    public ApiResponse<JsonNode> sync(@Parameter(description = "统计周期：weekly、monthly、annually、overall") @RequestParam(defaultValue = "annually") String mode,
            @Parameter(description = "基准 Unix 时间，秒；默认 0，overall 固定使用 0") @RequestParam(defaultValue = "0") long baseTime) {
        return ApiResponse.success(service.sync(mode, baseTime));
    }

    @Operation(summary = "查询微信读书统计")
    @GetMapping("/stats")
    public ApiResponse<JsonNode> stats(@Parameter(description = "统计周期：weekly、monthly、annually、overall") @RequestParam(defaultValue = "annually") String mode,
            @Parameter(description = "基准 Unix 时间，秒；默认 0，overall 固定使用 0") @RequestParam(defaultValue = "0") long baseTime) {
        return ApiResponse.success(service.stats(mode, baseTime));
    }

    @Operation(summary = "查询微信读书笔记")
    @GetMapping("/notes")
    public ApiResponse<JsonNode> notes(@Parameter(description = "微信读书书籍 ID") @RequestParam String bookId) {
        return ApiResponse.success(service.notes(bookId));
    }

    @Operation(summary = "查询微信读书书籍链接")
    @GetMapping("/book-link")
    public ApiResponse<WereadBookLinkVO> bookLink(@Parameter(description = "微信读书书籍 ID") @RequestParam String bookId) {
        return ApiResponse.success(service.bookLink(bookId));
    }

    @Operation(summary = "查询微信读书阅读进度")
    @GetMapping("/progress")
    public ApiResponse<JsonNode> progress(@Parameter(description = "微信读书书籍 ID") @RequestParam String bookId) {
        return ApiResponse.success(service.progress(bookId));
    }
}
