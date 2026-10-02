package top.aiolife.llm.pojo.req;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.*;
import java.util.*;
import lombok.Data;

/** ChatReq：接口专用字段。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatReq {
    @jakarta.validation.constraints.NotBlank
    private String prompt;
    private Long conversationId;
}
