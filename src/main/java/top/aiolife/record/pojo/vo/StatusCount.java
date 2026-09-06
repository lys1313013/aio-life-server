package top.aiolife.record.pojo.vo;

import lombok.Data;
import top.aiolife.record.pojo.enums.ProgressStatusEnum;

/**
 * 类功能描述
 *
 * @author Lys
 * @date 2025/10/11 23:24
 */
@Data
public class StatusCount {
    private ProgressStatusEnum status;
    private Integer count;
}
