package top.aiolife.relationship.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 人物详情（包含关系列表）
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PersonWithRelationships {
    private String id;
    private Long userId;
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
    private String createdAt;
    private String updatedAt;
    private List<RelationshipDetail> relationships;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RelationshipDetail {
        private Long id;
        private String relationType;
        private String direction;
        private String description;
        private String tags;
        private String createdAt;
        private PersonBasic target;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PersonBasic {
        private String id;
        private String name;
        private String avatar;
        private String category;
    }
}
