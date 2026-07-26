package top.aiolife.record.notification;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum NotificationBizType {
    LEETCODE_REMINDER("力扣未打卡提醒", true),
    LEETCODE_DAILY("力扣每日一题", true),
    FEEDBACK_ADMIN("新反馈通知", false),
    FEEDBACK_REPLY("反馈回复通知", false);

    private final String description;
    private final boolean requiresLeetCode;

    public static NotificationBizType fromName(String name) {
        return Arrays.stream(values())
                .filter(item -> item.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("不支持的通知类型：" + name));
    }
}
