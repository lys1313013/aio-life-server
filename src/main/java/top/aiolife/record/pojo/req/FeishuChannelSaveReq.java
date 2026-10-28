package top.aiolife.record.pojo.req;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import lombok.Data;

@Data
public class FeishuChannelSaveReq {
    @Min(0)
    @Max(1)
    private Integer isEnabled;
    private String appId;
    private String appSecret;
    private String openId;
}
