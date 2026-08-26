package top.aiolife.record.pojo.entity;

import top.aiolife.core.pojo.entity.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 类功能描述
 *
 * @author Lys
 * @date 2025-11-16 15:34
 */
@Data
@TableName("thought_rela_event")
public class ThoughtRelaEventEntity extends BaseEntity{

    private Long thoughtId;

    private String content;
}
