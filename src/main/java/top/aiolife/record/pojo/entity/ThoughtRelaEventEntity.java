package top.aiolife.record.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
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
@TableName("thought_rela_event")
public class ThoughtRelaEventEntity extends BaseEntity{

    private Long thoughtId;

    private String content;
}
