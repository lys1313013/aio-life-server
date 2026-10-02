package top.aiolife.relationship.pojo.vo;

import lombok.Data;

/** 接口专用字段。 */
@Data
public class RelationshipDetailVO {
    private Long id;
    private String relationType;
    private String direction;
    private String description;
    private String tags;
    private PersonBasicVO target;
}
