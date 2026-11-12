package top.aiolife.record.api;

import io.swagger.v3.oas.annotations.Operation;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.record.pojo.csdn.CsdnArticleVO;
import top.aiolife.record.pojo.csdn.CsdnStatsVO;
import top.aiolife.record.service.ICsdnService;

import java.util.List;

@Slf4j
@RestController
@AllArgsConstructor
@RequestMapping("/csdn")
@SaCheckLogin
public class CsdnController {

    private final ICsdnService csdnService;

    /**
     * 获取 CSDN 用户数据统计
     */
    @Operation(summary = "查询 CSDN 博客统计")
    @GetMapping("/stats")
    public ApiResponse<CsdnStatsVO> getCsdnStats(@RequestParam("username") String username) {
        return ApiResponse.success(csdnService.getCsdnStats(username));
    }

    /**
     * 获取 CSDN 用户最近文章列表
     */
    @Operation(summary = "查询 CSDN 最近文章")
    @GetMapping("/articles")
    public ApiResponse<List<CsdnArticleVO>> getCsdnArticles(
            @RequestParam("username") String username,
            @RequestParam(value = "limit", defaultValue = "20") Integer limit) {
        return ApiResponse.success(csdnService.getCsdnArticles(username, limit));
    }
}