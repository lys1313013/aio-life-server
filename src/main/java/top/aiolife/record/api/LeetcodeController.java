package top.aiolife.record.api;

import io.swagger.v3.oas.annotations.Operation;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.annotation.SaCheckRole;
import top.aiolife.core.resq.ApiResponse;
import top.aiolife.record.service.ILeetcodeService;
import jakarta.mail.MessagingException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@AllArgsConstructor
@RequestMapping("/leetcode")
public class LeetcodeController {

    private ILeetcodeService leetcodeService;

    /**
     * 手动发送力扣每日一题通知。
     */
    @Operation(summary = "管理员发送力扣每日一题通知")
    @PostMapping("/notifyTodayQuestion")
    @SaCheckRole("admin")
    public ApiResponse<Void> notifyTodayQuestion() throws MessagingException {
        StpUtil.getLoginIdAsLong();
        leetcodeService.notifyTodayQuestion();
        return ApiResponse.success();
    }
}
