package top.aiolife.record.api;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.record.pojo.req.WereadConnectionReq;
import top.aiolife.record.pojo.vo.WereadConnectionVO;
import top.aiolife.record.service.IWereadService;

/** 微信读书独立入口，所有数据使用当前登录用户的连接。 */
@RestController
@SaCheckLogin
@RequiredArgsConstructor
@RequestMapping("/weread")
public class WereadController {
    private final IWereadService service;

    @GetMapping("/connection")
    public ApiResponse<WereadConnectionVO> connection() {
        return ApiResponse.success(service.connection());
    }

    @PostMapping("/connection")
    public ApiResponse<WereadConnectionVO> connect(@RequestBody WereadConnectionReq request) {
        return ApiResponse.success(service.connect(request.getApiKey()));
    }

    @PostMapping("/disconnect")
    public ApiResponse<Void> disconnect() {
        service.disconnect();
        return ApiResponse.success();
    }

    @PostMapping("/sync")
    public ApiResponse<JsonNode> sync(@RequestParam(defaultValue = "annually") String mode,
            @RequestParam(defaultValue = "0") long baseTime) {
        return ApiResponse.success(service.sync(mode, baseTime));
    }

    @GetMapping("/stats")
    public ApiResponse<JsonNode> stats(@RequestParam(defaultValue = "annually") String mode,
            @RequestParam(defaultValue = "0") long baseTime) {
        return ApiResponse.success(service.stats(mode, baseTime));
    }

    @GetMapping("/notes")
    public ApiResponse<JsonNode> notes(@RequestParam String bookId) {
        return ApiResponse.success(service.notes(bookId));
    }

    @GetMapping("/progress")
    public ApiResponse<JsonNode> progress(@RequestParam String bookId) {
        return ApiResponse.success(service.progress(bookId));
    }
}
