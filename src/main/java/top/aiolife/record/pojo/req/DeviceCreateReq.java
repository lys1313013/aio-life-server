package top.aiolife.record.pojo.req;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;

/** DeviceCreateReq：仅包含接口允许写入的字段。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DeviceCreateReq {

    /**
     * 设备的名称
     */
    private String name;

    /**
     * 设备配置
     */
    private String spec;

    /**
     * 设备类型
     */
    private String type;

    /**
     * 设备状态
     */
    private String status;

    /**
     * 备注
     */
    private String remark;

    /**
     * 设备的购买日期
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate purchaseDate;

    /**
     * 设备的购买价格
     */
    private BigDecimal purchasePrice;

    /**
     * 设备的购买地点
     */
    private String purchasePlace;

    /**
     * 图片文件ID
     */
    private String fileId;

    /**
     * 设备的结束日期（用于计算日均费用）
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;
}
