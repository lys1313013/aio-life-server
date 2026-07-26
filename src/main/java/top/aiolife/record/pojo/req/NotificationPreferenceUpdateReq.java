package top.aiolife.record.pojo.req;

import lombok.Data;

import java.util.List;

@Data
public class NotificationPreferenceUpdateReq {
    private List<Item> items;

    @Data
    public static class Item {
        private String bizType;
        private String channel;
        private Boolean enabled;
    }
}
