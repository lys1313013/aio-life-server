package top.aiolife.relationship.convertor;

import java.util.List;
import java.util.Map;
import org.mapstruct.*;
import org.mapstruct.factory.Mappers;
import top.aiolife.relationship.pojo.entity.*;
import top.aiolife.relationship.pojo.vo.*;

@Mapper(builder = @Builder(disableBuilder = true), unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface RelationshipApiConvertor {
    RelationshipApiConvertor INSTANCE = Mappers.getMapper(RelationshipApiConvertor.class);
    PersonVO toPersonVO(PersonRelationship entity);
    List<PersonVO> toPersonVOList(List<PersonRelationship> entities);
    PersonDetailVO toPersonDetailVO(PersonWithRelationships entity);
    RelationshipVO toRelationshipVO(RelatesToRelationship entity);
    PersonBasicVO toPersonBasicVO(PersonRelationship entity);
    RelationshipDetailVO toRelationshipDetailVO(PersonWithRelationships.RelationshipDetail entity);
    PersonBasicVO toPersonBasicVO(PersonWithRelationships.PersonBasic entity);

    default PersonBasicVO toGraphNode(Map<String, Object> node) {
        PersonBasicVO result = new PersonBasicVO();
        result.setId((String) node.get("id"));
        result.setName((String) node.get("name"));
        result.setAvatar((String) node.get("avatar"));
        result.setCategory((String) node.get("category"));
        return result;
    }
    default GraphEdgeVO toGraphEdge(Map<String, Object> edge) {
        GraphEdgeVO result = new GraphEdgeVO();
        result.setSource((String) edge.get("source"));
        result.setTarget((String) edge.get("target"));
        result.setRelationType((String) edge.get("relationType"));
        result.setDirection((String) edge.get("direction"));
        result.setDescription((String) edge.get("description"));
        return result;
    }
    @SuppressWarnings("unchecked")
    default GraphVO toGraphVO(Map<String, Object> graph) {
        GraphVO result = new GraphVO();
        result.setNodes(((List<Map<String, Object>>) graph.get("nodes")).stream().map(this::toGraphNode).toList());
        result.setEdges(((List<Map<String, Object>>) graph.get("edges")).stream().map(this::toGraphEdge).toList());
        return result;
    }
}
