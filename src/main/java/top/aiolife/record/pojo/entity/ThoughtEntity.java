package top.aiolife.record.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import top.aiolife.core.pojo.entity.BaseEntity;

/**
 * 类功能描述
 *
 * @author Lys
 * @date 2025-11-16 15:34
 */
@Getter
@Setter
@ToString
@TableName("thought")
public class ThoughtEntity extends BaseEntity {

    private Long userId;

    private String content;

    private Integer isPinned;

    private Boolean hiddenContent;

    @TableField(exist = false)
    private List<ThoughtRelaEventEntity> events;
}
