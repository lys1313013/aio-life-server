package top.aiolife.record.pojo.entity;

import top.aiolife.core.pojo.entity.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.List;

/**
 * 类功能描述
 *
 * @author Lys
 * @date 2025-11-16 15:34
 */
@Data
@TableName("thought")
public class ThoughtEntity extends BaseEntity {

    private Long userId;

    private String content;

    private Integer isPinned;

    private Boolean hiddenContent;

    @TableField(exist = false)
    private List<ThoughtRelaEventEntity> events;
}
