package top.aiolife.membership.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import top.aiolife.core.pojo.entity.BaseEntity;

/** 系统统一维护的会员平台，图标仅引用受信任的内置资源。 */
@Getter
@Setter
@TableName("membership_provider")
public class MembershipProviderEntity extends BaseEntity {
    private String name;
    private String code;
    private String category;
    private String iconKey;
    private Integer sortOrder;
    private Integer isEnabled;
}
