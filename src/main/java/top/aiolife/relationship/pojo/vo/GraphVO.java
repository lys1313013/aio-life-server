package top.aiolife.relationship.pojo.vo;

import java.util.List;
import lombok.Data;

/** 接口专用字段。 */
@Data
public class GraphVO {
    private List<PersonBasicVO> nodes;
    private List<GraphEdgeVO> edges;
}
