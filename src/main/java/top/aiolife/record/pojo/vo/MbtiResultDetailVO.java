package top.aiolife.record.pojo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import lombok.Data;

/** 历史测试详情只返回页面使用的结果字段。 */
@Data
public class MbtiResultDetailVO {
    private Long id;
    private String testId;
    private String mbtiType;
    private String resultsPage;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    private JsonNode predictions;
    private JsonNode traitOrderConscious;
    private JsonNode traitOrderShadow;
    private JsonNode matches;
}
