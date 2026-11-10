package top.aiolife.record.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import top.aiolife.core.pojo.entity.BaseEntity;

/**
 * 备忘录实体
 *
 * @author Lys
 * @date 2025/12/07 14:35
 */
@Getter
@Setter
@ToString
@TableName("memo")
public class MemoEntity extends BaseEntity {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 标题
     */
    private String title;

    /**
     * 内容
     */
    private String content;

    /**
     * 是否隐藏内容
     */
    private Boolean hiddenContent;
}
