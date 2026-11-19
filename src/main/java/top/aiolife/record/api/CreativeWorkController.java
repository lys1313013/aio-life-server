package top.aiolife.record.api;

import cn.dev33.satoken.annotation.SaCheckLogin;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import java.util.List;
import top.aiolife.record.pojo.vo.RankingOptionVO;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.aiolife.core.id.IdCodec;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.core.web.CurrentUserId;
import top.aiolife.record.service.CreativeWorkService;

@RestController
@SaCheckLogin
@RequiredArgsConstructor
@RequestMapping("/creative-works")
public class CreativeWorkController {
    private final CreativeWorkService works;

    @GetMapping("/rankings")
    @Operation(summary = "查询作品可选榜单")
    public ApiResponse<List<RankingOptionVO>> rankings(@RequestParam String media) {
        return ApiResponse.success(works.rankings(media));
    }

    @GetMapping("/{id}/cover")
    @Operation(summary = "读取共享作品封面")
    public void cover(@PathVariable String id, HttpServletResponse response) {
        works.writeCover(IdCodec.decode(id), response);
    }

    @PostMapping("/{id}/cover/retry")
    @Operation(summary = "重试作品封面下载")
    public ApiResponse<Void> retry(@PathVariable String id, @CurrentUserId Long userId) {
        works.retryCover(IdCodec.decode(id), userId);
        return ApiResponse.success();
    }
}
