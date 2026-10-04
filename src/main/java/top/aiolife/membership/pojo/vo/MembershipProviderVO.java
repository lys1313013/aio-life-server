package top.aiolife.membership.pojo.vo;

import lombok.Data;

@Data
public class MembershipProviderVO {
    private Long id;
    private String name;
    private String code;
    private String category;
    private String iconKey;
    private Integer sortOrder;
    private Integer isEnabled;
}
