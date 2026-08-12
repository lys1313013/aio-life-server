package top.aiolife.record.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 电子产品
 *
 * @author Lys
 * @date 2025/04/04 19:14
 */
@Getter
@Setter
@TableName("device")
public class DeviceEntity {
    /**
     * 唯一标识
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

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
    private String purchaseDate;

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
    private String endDate;
}
