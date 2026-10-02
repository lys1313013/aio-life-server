package top.aiolife.relationship.pojo.req;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 关系创建/更新请求
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RelationshipReq {

    private String sourcePersonId;
    private String targetPersonId;
    private String relationType;
    private String direction;
    private String description;
    private String tags;
}
