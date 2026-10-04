package top.aiolife.record.pojo.req;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.*;
import java.util.*;
import lombok.Data;

/** MbtiResultSaveReq：接口专用字段。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MbtiResultSaveReq {
    private String testId;
    private String mbtiType;
    private String resultsPage;
    private JsonNode predictions;
    private JsonNode traitOrderConscious;
    private JsonNode traitOrderShadow;
    private JsonNode matches;
}
