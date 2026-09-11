package top.aiolife.record.pojo.req;

import com.alibaba.fastjson2.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

/** 凭证不参与日志序列化，也不会回传客户端。 */
@Getter
@Setter
public class WereadConnectionReq {
    @JSONField(serialize = false)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String apiKey;
}
