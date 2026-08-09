package top.aiolife.relationship.service.impl;

import lombok.RequiredArgsConstructor;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.Result;
import org.neo4j.driver.Record;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.neo4j.core.Neo4jTemplate;
import org.springframework.stereotype.Service;
import top.aiolife.relationship.pojo.entity.PersonRelationship;
import top.aiolife.relationship.pojo.entity.PersonWithRelationships;
import top.aiolife.relationship.repository.PersonRepository;
import top.aiolife.relationship.service.IPersonService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 人物 Service 实现
 */
@Service
@ConditionalOnProperty(name = "aio.life.neo4j.enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
public class PersonServiceImpl implements IPersonService {

    private final PersonRepository personRepository;
    private final Neo4jTemplate neo4jTemplate;
    private final Driver driver;

    @Override
    public List<PersonRelationship> getAllPersons(Long userId) {
        return personRepository.findByUserId(userId);
    }

    @Override
    public Optional<PersonRelationship> getPersonById(Long userId, String personId) {
        return personRepository.findById(personId);
    }

    @Override
    public List<PersonRelationship> searchPersons(Long userId, String keyword) {
        return personRepository.searchByName(userId, keyword);
    }

    @Override
    public PersonRelationship createPerson(PersonRelationship person) {
        person.setId(UUID.randomUUID().toString());
        person.setCreatedAt(LocalDateTime.now());
        person.setUpdatedAt(LocalDateTime.now());
        return personRepository.save(person);
    }

    @Override
    public PersonRelationship updatePerson(Long userId, PersonRelationship person) {
        PersonRelationship existing = personRepository.findById(person.getId())
                .orElseThrow(() -> new RuntimeException("人物不存在"));
        if (!existing.getUserId().equals(userId)) {
            throw new RuntimeException("无权操作该人物");
        }
        person.setCreatedAt(existing.getCreatedAt());
        person.setUpdatedAt(LocalDateTime.now());
        return personRepository.save(person);
    }

    @Override
    public void deletePerson(Long userId, String personId) {
        PersonRelationship existing = personRepository.findById(personId)
                .orElseThrow(() -> new RuntimeException("人物不存在"));
        if (!existing.getUserId().equals(userId)) {
            throw new RuntimeException("无权操作该人物");
        }
        try (Session session = driver.session()) {
            session.run("MATCH (p:Person {id: $id, userId: $userId})-[r:RELATES_TO]-() DELETE r", 
                        Map.of("id", personId, "userId", userId));
        }
        personRepository.deleteById(personId);
    }

    private String toString(org.neo4j.driver.Value value) {
        // Value.toString() 会给字符串带 Cypher 风格的双引号，需先 asObject() 解包
        if (value == null || value.isNull()) return null;
        Object obj = value.asObject();
        return obj == null ? null : obj.toString();
    }

    public Map<String, Object> getGraphData(Long userId) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();

        try (Session session = driver.session()) {
            Result personResult = session.run(
                "MATCH (p:Person {userId: $userId}) RETURN p.id as id, p.name as name, p.avatar as avatar, p.category as category",
                Map.of("userId", userId)
            );

            while (personResult.hasNext()) {
                Record record = personResult.next();
                Map<String, Object> node = new HashMap<>();
                node.put("id", record.get("id").asString());
                node.put("name", record.get("name").isNull() ? "" : record.get("name").asString());
                node.put("avatar", record.get("avatar").isNull() ? null : record.get("avatar").asString());
                node.put("category", record.get("category").isNull() ? null : record.get("category").asString());
                nodes.add(node);
            }

            Result relResult = session.run(
                "MATCH (p1:Person {userId: $userId})-[r:RELATES_TO]->(p2:Person {userId: $userId}) RETURN p1.id as source, p2.id as target, r.relationType as relationType, r.direction as direction, r.description as description",
                Map.of("userId", userId)
            );

            while (relResult.hasNext()) {
                Record record = relResult.next();
                Map<String, Object> edge = new HashMap<>();
                edge.put("source", record.get("source").asString());
                edge.put("target", record.get("target").asString());
                edge.put("relationType", record.get("relationType").isNull() ? "" : record.get("relationType").asString());
                edge.put("direction", record.get("direction").isNull() ? "双向" : record.get("direction").asString());
                edge.put("description", record.get("description").isNull() ? "" : record.get("description").asString());
                edges.add(edge);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("nodes", nodes);
        result.put("edges", edges);
        return result;
    }

    public PersonWithRelationships getPersonWithRelationships(Long userId, String personId) {
        try (Session session = driver.session()) {
            Result personResult = session.run(
                "MATCH (p:Person {id: $id, userId: $userId}) RETURN p",
                Map.of("id", personId, "userId", userId)
            );

            if (!personResult.hasNext()) {
                return null;
            }

            var personNode = personResult.next().get("p").asNode();
            PersonWithRelationships result = new PersonWithRelationships();
            result.setId(personNode.get("id").asString());
            result.setUserId(personNode.get("userId").asLong());
            result.setName(toString(personNode.get("name")));
            result.setAvatar(toString(personNode.get("avatar")));
            result.setCategory(toString(personNode.get("category")));
            result.setDescription(toString(personNode.get("description")));
            result.setTags(toString(personNode.get("tags")));
            result.setBirthday(toString(personNode.get("birthday")));
            result.setPhone(toString(personNode.get("phone")));
            result.setEmail(toString(personNode.get("email")));
            result.setSchool(toString(personNode.get("school")));
            result.setSocialLinks(toString(personNode.get("socialLinks")));
            result.setNotes(toString(personNode.get("notes")));
            result.setCreatedAt(toString(personNode.get("createdAt")));
            result.setUpdatedAt(toString(personNode.get("updatedAt")));

            Result relResult = session.run(
                "MATCH (p:Person {id: $id})-[r:RELATES_TO]-(other:Person) RETURN r, other",
                Map.of("id", personId)
            );

            List<PersonWithRelationships.RelationshipDetail> relationships = new ArrayList<>();
            while (relResult.hasNext()) {
                Record relRecord = relResult.next();
                var rel = relRecord.get("r").asRelationship();
                var other = relRecord.get("other").asNode();

                PersonWithRelationships.RelationshipDetail rd = new PersonWithRelationships.RelationshipDetail();
                rd.setId(rel.id());
                rd.setRelationType(toString(rel.get("relationType")));
                rd.setDirection(toString(rel.get("direction")));
                rd.setDescription(toString(rel.get("description")));
                rd.setTags(toString(rel.get("tags")));
                rd.setCreatedAt(toString(rel.get("createdAt")));

                PersonWithRelationships.PersonBasic target = new PersonWithRelationships.PersonBasic();
                target.setId(other.get("id").asString());
                target.setName(toString(other.get("name")));
                target.setAvatar(toString(other.get("avatar")));
                target.setCategory(toString(other.get("category")));
                rd.setTarget(target);

                relationships.add(rd);
            }
            result.setRelationships(relationships);

            return result;
        }
    }
}
