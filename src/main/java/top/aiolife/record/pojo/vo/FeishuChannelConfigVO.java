package top.aiolife.record.pojo.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FeishuChannelConfigVO {
    private boolean configured;
    private Integer isEnabled;
    private boolean bound;
    private String appId;
    private String receiverOpenId;
    private String receiverName;
    private String openIdMasked;
}
