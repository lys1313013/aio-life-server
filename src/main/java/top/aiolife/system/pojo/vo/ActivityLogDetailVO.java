package top.aiolife.system.pojo.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonRawValue;
import lombok.Getter;
import lombok.Setter;

/** 只在单条操作日志详情中返回已验证的脱敏 JSON。 */
@Getter
@Setter
public class ActivityLogDetailVO extends ActivityLogVO {
    @JsonRawValue
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private String requestParams;
}
