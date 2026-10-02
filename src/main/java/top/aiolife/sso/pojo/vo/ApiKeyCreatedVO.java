package top.aiolife.sso.pojo.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import lombok.Data;

/** ApiKeyCreatedVO：仅包含接口需要返回的字段。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiKeyCreatedVO {


    /**
     * API Key
     */
    private String apiKey;


}
