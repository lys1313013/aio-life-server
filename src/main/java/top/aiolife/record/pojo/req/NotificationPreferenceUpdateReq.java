package top.aiolife.record.pojo.req;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import lombok.Data;

import java.util.List;

@Data
public class NotificationPreferenceUpdateReq {
    private List<Item> items;

    @Data
    public static class Item {
        private String bizType;
        private String channel;
        @Min(0)
    @Max(1)
    private Integer isEnabled;
    }
}
