package top.aiolife.bankcard.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import top.aiolife.core.pojo.entity.BaseEntity;

/** 银行卡与用户私有标签的关联。 */
@Getter
@Setter
@TableName("bank_card_tag_rel")
public class BankCardTagRelEntity extends BaseEntity {
    private Long userId;
    private Long bankCardId;
    private Long tagId;
}
