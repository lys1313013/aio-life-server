package top.aiolife.scheduling.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import top.aiolife.record.service.ILeetcodeService;

/** 仅允许执行明确注册的业务方法，数据库不保存可执行代码。 */
@Component
@RequiredArgsConstructor
public class ScheduledJobRegistry {
    public static final String CHECK = "leetcode.check";
    public static final String DAILY = "leetcode.daily-question";
    private final ILeetcodeService leetcode;

    public List<String> codes() { return List.of(CHECK, DAILY); }

    public void requireKnown(String code) {
        if (!codes().contains(code)) throw new IllegalArgumentException("未注册的定时任务");
    }

    public void execute(String code) {
        switch (code) {
            case CHECK -> leetcode.check();
            case DAILY -> leetcode.notifyTodayQuestion();
            default -> throw new IllegalArgumentException("未注册的定时任务");
        }
    }
}
