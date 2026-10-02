package top.aiolife.record.pojo.req;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.*;
import java.util.*;
import lombok.Data;

/** CbtiTestReq：接口专用字段。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CbtiTestReq {
    private Map<Integer, Integer> answers;
    private Map<String, Object> hiddenAnswers;
}
