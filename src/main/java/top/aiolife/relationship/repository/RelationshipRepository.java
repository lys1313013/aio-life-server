package top.aiolife.relationship.repository;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import top.aiolife.relationship.pojo.entity.RelatesToRelationship;

import java.util.List;

/**
 * 关系 Repository
 */
public interface RelationshipRepository extends Neo4jRepository<RelatesToRelationship, String> {

    /**
     * 查询两个人之间的关系
     */
    @Query("MATCH (p1:Person)-[r:RELATES_TO]-(p2:Person) WHERE p1.id = $personId1 AND p2.id = $personId2 AND p1.userId = $userId RETURN r")
    List<RelatesToRelationship> findRelationBetween(@Param("personId1") String personId1, @Param("personId2") String personId2, @Param("userId") Long userId);

    /**
     * 查询某个人的所有关系
     */
    @Query("MATCH (p:Person)-[r:RELATES_TO]-(other:Person) WHERE p.id = $personId AND p.userId = $userId RETURN r, other")
    List<RelatesToRelationship> findAllByPersonId(@Param("personId") String personId, @Param("userId") Long userId);

    /**
     * 删除特定关系
     */
    @Query("MATCH (p1:Person)-[r:RELATES_TO]-(p2:Person) WHERE p1.id = $personId1 AND p2.id = $personId2 AND p1.userId = $userId DELETE r")
    void deleteRelationBetween(@Param("personId1") String personId1, @Param("personId2") String personId2, @Param("userId") Long userId);
}
