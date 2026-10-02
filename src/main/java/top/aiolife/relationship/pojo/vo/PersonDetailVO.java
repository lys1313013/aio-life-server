package top.aiolife.relationship.pojo.vo;

import java.util.List;
import lombok.Data;

/** 接口专用字段。 */
@Data
public class PersonDetailVO {
    private String id;
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
    private List<RelationshipDetailVO> relationships;
}
