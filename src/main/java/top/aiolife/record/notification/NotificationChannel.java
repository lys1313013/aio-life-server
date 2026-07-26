package top.aiolife.record.notification;

import java.util.List;

public final class NotificationChannel {
    private NotificationChannel() {}

    public static final String STATION = "STATION";
    public static final String EMAIL = "EMAIL";
    public static final String FEISHU = "FEISHU";

    public static final List<String> ALL = List.of(STATION, EMAIL, FEISHU);
}
