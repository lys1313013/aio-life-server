package top.aiolife.record.pojo.vo;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import lombok.Data;

/** 查询预览仅返回数值和实际变量，不返回不必要的业务明细。 */
@Data
public class GoalProgressPreviewVO {
    private BigDecimal currentValue;
    private boolean achieved;
    private int progressPercent;
    private JsonNode variables;
}
