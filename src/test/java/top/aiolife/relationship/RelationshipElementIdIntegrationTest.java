package top.aiolife.relationship;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.data.neo4j.core.Neo4jTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import top.aiolife.core.web.CurrentUserIdArgumentResolver;
import top.aiolife.config.JsonConfig;
import top.aiolife.relationship.api.RelationshipController;
import top.aiolife.relationship.pojo.entity.RelatesToRelationship;
import top.aiolife.relationship.repository.PersonRepository;
import top.aiolife.relationship.repository.RelationshipRepository;
import top.aiolife.relationship.service.impl.PersonServiceImpl;
import top.aiolife.relationship.service.impl.RelationshipServiceImpl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 自建隔离 Neo4j，验证 elementId 在驱动、HTTP 响应及更新路径间完整往返。 */
@Tag("integration")
class RelationshipElementIdIntegrationTest {
    private static final GenericContainer<?> NEO4J = new GenericContainer<>(
            System.getProperty("aio.life.test.neo4j.image", "neo4j:5.26.0"))
            .withEnv("NEO4J_AUTH", "none")
            .withEnv("NEO4J_server_memory_heap_initial__size", "128m")
            .withEnv("NEO4J_server_memory_heap_max__size", "256m")
            .withEnv("NEO4J_server_memory_pagecache_size", "64m")
            .withExposedPorts(7687)
            .waitingFor(Wait.forLogMessage(".*Started\\.\\n", 1))
            .withStartupTimeout(Duration.ofMinutes(3));
    private static Driver driver;
    private final ObjectMapper json = new JsonConfig().jacksonObjectMapper(new Jackson2ObjectMapperBuilder());
    private RelationshipServiceImpl relationships;
    private MockMvc mvc;

    @BeforeAll
    static void startNeo4j() {
        NEO4J.start();
        driver = GraphDatabase.driver("bolt://" + NEO4J.getHost() + ":" + NEO4J.getMappedPort(7687), AuthTokens.none());
        driver.verifyConnectivity();
    }

    @AfterAll
    static void stopNeo4j() {
        if (driver != null) driver.close();
        NEO4J.stop();
    }

    @BeforeEach
    void setup() {
        try (var session = driver.session()) {
            session.run("MATCH (n) DETACH DELETE n").consume();
            session.run("CREATE (:Person {id:'source', userId:1, name:'来源'}), "
                    + "(:Person {id:'target', userId:1, name:'目标'}), "
                    + "(:Person {id:'foreign', userId:2, name:'其他用户'})").consume();
        }
        var persons = new PersonServiceImpl(mock(PersonRepository.class), mock(Neo4jTemplate.class), driver);
        relationships = new RelationshipServiceImpl(mock(RelationshipRepository.class), mock(PersonRepository.class), driver);
        mvc = MockMvcBuilders.standaloneSetup(new RelationshipController(persons, relationships))
                .setCustomArgumentResolvers(new CurrentUserIdArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(json)).build();
    }

    @Test
    void 创建详情更新使用同一字符串标识且更新只影响指定关系() throws Exception {
        try (var auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsLong).thenReturn(1L);
            auth.when(StpUtil::getLoginIdAsString).thenReturn("1");
            var created = mvc.perform(post("/relationships").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"sourcePersonId\":\"source\",\"targetPersonId\":\"target\",\"relationType\":\"朋友\"}"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            var response = json.readTree(created);
            assertEquals(0, response.path("code").asInt());
            assertTrue(response.at("/data/id").isTextual());
            String id = response.at("/data/id").asText();
            assertFalse(id.isBlank());
            try (var session = driver.session()) {
                String stored = session.run("MATCH ()-[r:RELATES_TO]->() RETURN elementId(r) AS id")
                        .single().get("id").asString();
                assertEquals(stored, id);
            }

            var detail = mvc.perform(get("/relationships/persons/source")).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertEquals(id, json.readTree(detail).at("/data/relationships/0/id").asText());
            var other = new RelatesToRelationship();
            other.setRelationType("同事");
            String otherId = relationships.createRelationship(other, 1L, "source", "target").getId();
            assertNotEquals(id, otherId);

            var updated = mvc.perform(put("/relationships/{id}", id).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"relationType\":\"家人\",\"description\":\"更新\"}"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertEquals(id, json.readTree(updated).at("/data/id").asText());
            try (var session = driver.session()) {
                assertEquals("家人", session.run("MATCH ()-[r:RELATES_TO]->() WHERE elementId(r)=$id RETURN r.relationType AS type",
                        Map.of("id", id)).single().get("type").asString());
                assertEquals("同事", session.run("MATCH ()-[r:RELATES_TO]->() WHERE elementId(r)=$id RETURN r.relationType AS type",
                        Map.of("id", otherId)).single().get("type").asString());
            }
        }
    }

    @Test
    void 跨用户和不存在的关系标识不能更新且已有关系直接可用() {
        String id;
        try (var session = driver.session()) {
            id = session.run("MATCH (a:Person {id:'source'}), (b:Person {id:'target'}) "
                    + "CREATE (a)-[r:RELATES_TO {relationType:'原有关系'}]->(b) RETURN elementId(r) AS id")
                    .single().get("id").asString();
        }
        var update = new RelatesToRelationship();
        update.setRelationType("更新");
        assertThrows(RuntimeException.class, () -> relationships.updateRelationship(2L, id, update));
        assertThrows(RuntimeException.class, () -> relationships.updateRelationship(1L, "missing", update));
        assertEquals(id, relationships.updateRelationship(1L, id, update).getId());
    }
}
