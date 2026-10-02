package top.aiolife.relationship.pojo.vo;

import lombok.Data;

/** 接口专用字段。 */
@Data
public class GraphEdgeVO {
    private String source;
    private String target;
    private String relationType;
    private String direction;
    private String description;
}
