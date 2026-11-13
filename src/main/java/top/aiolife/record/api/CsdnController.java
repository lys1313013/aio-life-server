package top.aiolife.record.api;

import cn.dev33.satoken.annotation.SaCheckLogin;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.record.pojo.csdn.CsdnArticleVO;
import top.aiolife.record.pojo.csdn.CsdnStatsVO;
import top.aiolife.record.service.ICsdnService;

/**
 * CSDN 博客数据控制器
 */
@Tag(name = "CSDN")
@RestController
@AllArgsConstructor
@RequestMapping("/csdn")
@SaCheckLogin
public class CsdnController {

    private final ICsdnService csdnService;

    @Operation(summary = "查询 CSDN 博客统计")
    @GetMapping("/stats")
    public ApiResponse<CsdnStatsVO> getCsdnStats(@RequestParam("username") String username) {
        return ApiResponse.success(csdnService.getCsdnStats(username));
    }

    @Operation(summary = "查询 CSDN 最近文章")
    @GetMapping("/articles")
    public ApiResponse<List<CsdnArticleVO>> getCsdnArticles(
            @RequestParam("username") String username,
            @RequestParam(value = "limit", defaultValue = "20") Integer limit) {
        return ApiResponse.success(csdnService.getCsdnArticles(username, limit));
    }
}
