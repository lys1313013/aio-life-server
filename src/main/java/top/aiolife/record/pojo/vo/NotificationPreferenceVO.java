package top.aiolife.record.pojo.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class NotificationPreferenceVO {
    private String bizType;
    private String description;
    private boolean visible;
    private List<ChannelState> channels;

    @Data
    @Builder
    public static class ChannelState {
        private String channel;
        private boolean enabled;
    }
}
