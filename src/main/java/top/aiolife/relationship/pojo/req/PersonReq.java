package top.aiolife.relationship.pojo.req;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 人物创建/更新请求
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PersonReq {
    private Long id;
    private String name;
    private String avatar;
    private String category;
    private String description;
    private String tags;
    private String birthday;
    private String phone;
    private String email;
    private String school;
    private String socialLinks;
    private String notes;
}
