package top.aiolife.relationship.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Property;
import org.springframework.data.neo4j.core.schema.RelationshipProperties;
import org.springframework.data.neo4j.core.schema.TargetNode;

import java.time.LocalDateTime;

/**
 * 关系边
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@RelationshipProperties
public class RelationshipEdge {

    @Id
    @GeneratedValue
    private String id;

    /**
     * 用户ID（所属用户）
     */
    @Property("userId")
    private Long userId;

    /**
     * 关系类型
     */
    @Property("relationType")
    private String relationType;

    /**
     * 关系方向：单向/双向
     */
    @Property("direction")
    private String direction;

    /**
     * 关系描述
     */
    @Property("description")
    private String description;

    /**
     * 标签，逗号分隔
     */
    @Property("tags")
    private String tags;

    /**
     * 创建时间
     */
    @Property("createdAt")
    private LocalDateTime createdAt;
}
