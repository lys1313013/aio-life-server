package top.aiolife.relationship.pojo.req;

import lombok.Data;

/** 接口专用字段。 */
@Data
public class RelationshipDeleteReq {
    private String sourcePersonId;
    private String targetPersonId;
}
