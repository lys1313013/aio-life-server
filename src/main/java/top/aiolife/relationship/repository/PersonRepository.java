package top.aiolife.relationship.repository;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import top.aiolife.relationship.pojo.entity.PersonRelationship;

import java.util.List;
import java.util.Optional;

/**
 * 人物节点 Repository
 */
public interface PersonRepository extends Neo4jRepository<PersonRelationship, String> {
    @Query("MATCH (p:Person) WHERE p.userId = $userId DETACH DELETE p")
    void eraseByUserId(@Param("userId") Long userId);


    /**
     * 查询用户的所有人物
     */
    List<PersonRelationship> findByUserId(Long userId);

    /**
     * 根据用户ID和姓名查找
     */
    Optional<PersonRelationship> findByUserIdAndName(Long userId, String name);

    /**
     * 搜索人物（模糊匹配姓名）
     */
    @Query("MATCH (p:Person) WHERE p.userId = $userId AND p.name CONTAINS $keyword RETURN p")
    List<PersonRelationship> searchByName(@Param("userId") Long userId, @Param("keyword") String keyword);
}
