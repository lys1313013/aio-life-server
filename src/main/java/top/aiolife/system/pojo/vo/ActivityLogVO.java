package top.aiolife.system.pojo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import lombok.Data;

/** ActivityLogVO：仅包含接口需要返回的字段。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ActivityLogVO {

    private Long userId;

    private Long id;

    private String logType;

    private String username;

    private String nickname;

    private String ipAddress;

    private String browser;

    private String functionName;

    private String functionItem;

    private String accessType;

    private String requestPath;

    private Boolean success;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
