package top.aiolife.record.pojo.entity;

import top.aiolife.core.pojo.entity.BaseEntity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 任务列实体类
 *
 * @author Lys
 * @date 2025/04/10 22:44
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("task_column")
public class TaskColumnEntity extends BaseEntity {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 列标题
     */
    private String title;

    /**
     * 排序
     */
    private Integer sortOrder;

    /**
     * 背景颜色
     */
    private String bgColor;

}
