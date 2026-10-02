package top.aiolife.record.pojo.query;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;

/** DeviceQuery：仅包含接口支持筛选的字段。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DeviceQuery {

    /**
     * 设备类型
     */
    private String type;
}
