package top.aiolife.relationship.pojo.req;

import lombok.Data;

/** 接口专用字段。 */
@Data
public class RelationshipUpdateReq {
    private String relationType;
    private String direction;
    private String description;
    private String tags;
}
