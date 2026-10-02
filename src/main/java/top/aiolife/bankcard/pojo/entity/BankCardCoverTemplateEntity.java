package top.aiolife.bankcard.pojo.entity;

import lombok.Getter;
import lombok.Setter;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import top.aiolife.core.pojo.entity.BaseEntity;

/** 公共卡面持久化记录，图片通过 file 反向关联。 */
@Getter
@Setter
@TableName("bank_card_cover_template")
public class BankCardCoverTemplateEntity extends BaseEntity {
    private String name;
    private Long bankId;
    private String cardType;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String sourceUrl;
    private Integer isEnabled;
    private Integer sortOrder;
}
