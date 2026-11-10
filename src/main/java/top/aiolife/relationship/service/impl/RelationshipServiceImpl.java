package top.aiolife.relationship.service.impl;

import lombok.RequiredArgsConstructor;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import top.aiolife.relationship.pojo.entity.RelatesToRelationship;
import top.aiolife.relationship.repository.PersonRepository;
import top.aiolife.relationship.repository.RelationshipRepository;
import top.aiolife.relationship.service.IRelationshipService;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 关系 Service 实现
 */
@Service
@ConditionalOnProperty(name = "aio.life.neo4j.enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
public class RelationshipServiceImpl implements IRelationshipService {

    private final RelationshipRepository relationshipRepository;
    private final PersonRepository personRepository;
    private final Driver driver;

    @Override
    public RelatesToRelationship createRelationship(RelatesToRelationship relationship, Long userId, String sourcePersonId, String targetPersonId) {
        try (Session session = driver.session()) {
            // 先验证两个人物存在且属于该用户
            var sourceCheck = session.run(
                "MATCH (p:Person {id: $sourceId, userId: $userId}) RETURN p",
                Map.of("sourceId", sourcePersonId, "userId", userId)
            );
            var targetCheck = session.run(
                "MATCH (p:Person {id: $targetId, userId: $userId}) RETURN p",
                Map.of("targetId", targetPersonId, "userId", userId)
            );

            if (!sourceCheck.hasNext() || !targetCheck.hasNext()) {
                throw new RuntimeException("Source or target person not found");
            }

            // 创建关系
            var result = session.run(
                """
                MATCH (source:Person {id: $sourceId}), (target:Person {id: $targetId})
                CREATE (source)-[r:RELATES_TO {
                    userId: $userId,
                    relationType: $relationType,
                    direction: $direction,
                    description: $description,
                    tags: $tags,
                    createdAt: datetime()
                }]->(target)
                RETURN elementId(r) as id, r.relationType as relationType, r.direction as direction, r.description as description, r.tags as tags, r.createdAt as createdAt
                """,
                Map.of(
                    "sourceId", sourcePersonId,
                    "targetId", targetPersonId,
                    "userId", userId,
                    "relationType", relationship.getRelationType() != null ? relationship.getRelationType() : "",
                    "direction", relationship.getDirection() != null ? relationship.getDirection() : "双向",
                    "description", relationship.getDescription() != null ? relationship.getDescription() : "",
                    "tags", relationship.getTags() != null ? relationship.getTags() : ""
                )
            );

            if (result.hasNext()) {
                var record = result.next();
                relationship.setId(record.get("id").asString());
                relationship.setRelationType(record.get("relationType").asString());
                relationship.setDirection(record.get("direction").asString());
                relationship.setDescription(record.get("description").isNull() ? "" : record.get("description").asString());
                relationship.setTags(record.get("tags").isNull() ? "" : record.get("tags").asString());
                relationship.setCreatedAt(LocalDateTime.now());
            }

            return relationship;
        }
    }

    @Override
    public RelatesToRelationship updateRelationship(Long userId, String relationshipId, RelatesToRelationship relationship) {
        try (Session session = driver.session()) {
            var check = session.run(
                "MATCH (p:Person {userId: $userId})-[r:RELATES_TO]-() WHERE elementId(r) = $id RETURN elementId(r)",
                Map.of("id", relationshipId, "userId", userId)
            );
            if (!check.hasNext()) {
                throw new RuntimeException("无权操作该关系或关系不存在");
            }

            session.run(
                """
                MATCH ()-[r:RELATES_TO]->()
                WHERE elementId(r) = $id
                SET r.relationType = $relationType, r.direction = $direction, r.description = $description, r.tags = $tags
                """,
                Map.of(
                    "id", relationshipId,
                    "relationType", relationship.getRelationType() != null ? relationship.getRelationType() : "",
                    "direction", relationship.getDirection() != null ? relationship.getDirection() : "双向",
                    "description", relationship.getDescription() != null ? relationship.getDescription() : "",
                    "tags", relationship.getTags() != null ? relationship.getTags() : ""
                )
            );
            relationship.setId(relationshipId);
            return relationship;
        }
    }

    @Override
    public void deleteRelationship(Long userId, String sourcePersonId, String targetPersonId) {
        try (Session session = driver.session()) {
            session.run(
                """
                MATCH (source:Person {id: $sourceId, userId: $userId})-[r:RELATES_TO]-(target:Person {id: $targetId})
                DELETE r
                """,
                Map.of(
                    "sourceId", sourcePersonId,
                    "targetId", targetPersonId,
                    "userId", userId
                )
            );
        }
    }
}
