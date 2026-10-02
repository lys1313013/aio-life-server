package top.aiolife.llm.pojo.req;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.*;
import java.util.*;
import lombok.Data;

/** ChatSessionSaveReq：接口专用字段。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatSessionSaveReq {
    private String title;
}
