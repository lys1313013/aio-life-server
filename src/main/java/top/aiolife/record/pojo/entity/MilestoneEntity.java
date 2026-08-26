package top.aiolife.record.pojo.entity;

import top.aiolife.core.pojo.entity.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * 类功能描述
 *
 * @author Lys
 * @date 2026-02-07 16:31
 */
@Getter
@Setter
@TableName("milestone")
public class MilestoneEntity extends BaseEntity {
    private Long userId;

    private String title;

    private String description;

    private String date;

    private String end_date;

    private String type;

    private String tags;
}
