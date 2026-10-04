package top.aiolife.system.pojo.entity;


import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import top.aiolife.core.pojo.entity.BaseEntity;

@Getter
@Setter
@TableName("user_home_card_preference")
public class HomeCardPreferenceEntity extends BaseEntity {
    private Long userId;
    private String cardKey;
    private Boolean enabled;
    private Integer sortOrder;
}
