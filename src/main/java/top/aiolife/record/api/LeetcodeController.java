package top.aiolife.record.api;

import cn.dev33.satoken.stp.StpUtil;
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
     * 没啥地方使用，只是用来手动触发
     */
    @PostMapping("/notifyTodayQuestion")
    public ApiResponse<Void> notifyTodayQuestion() throws MessagingException {
        StpUtil.getLoginIdAsLong();
        leetcodeService.notifyTodayQuestion();
        return ApiResponse.success();
    }
}
