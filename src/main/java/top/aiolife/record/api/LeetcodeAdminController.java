package top.aiolife.record.api;

import cn.dev33.satoken.annotation.SaCheckRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.record.service.ILeetcodeService;

/**
 * 力扣管理控制器
 *
 * @author Lys
 * @date 2025/04/10 00:05
 */
@Tag(name = "LeetCode 管理")
@RestController
@RequiredArgsConstructor
@RequestMapping("/leetcode")
@SaCheckRole("admin")
public class LeetcodeAdminController {

    private final ILeetcodeService leetcodeService;

    @Operation(summary = "管理员发送力扣每日一题通知")
    @PostMapping("/notifyTodayQuestion")
    public ApiResponse<Void> notifyTodayQuestion() {
        leetcodeService.notifyTodayQuestion();
        return ApiResponse.success();
    }
}
