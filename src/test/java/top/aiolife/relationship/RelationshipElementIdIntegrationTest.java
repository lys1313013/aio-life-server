package top.aiolife.relationship;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
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
import org.springframework.boot.autoconfigure.data.neo4j.Neo4jDataAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
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
import top.aiolife.config.Neo4jConfig;
import top.aiolife.export.dataset.RelationshipDatasets;
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

/** 在隔离 Neo4j 验证图谱读写和增量迁移。 */
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
    private static AnnotationConfigApplicationContext graphContext;
    private final ObjectMapper json = new JsonConfig().jacksonObjectMapper(new Jackson2ObjectMapperBuilder());
    private RelationshipServiceImpl relationships;
    private MockMvc mvc;

    @BeforeAll
    static void startNeo4j() {
        NEO4J.start();
        driver = GraphDatabase.driver("bolt://" + NEO4J.getHost() + ":" + NEO4J.getMappedPort(7687), AuthTokens.none());
        driver.verifyConnectivity();
        graphContext = new AnnotationConfigApplicationContext();
        graphContext.getEnvironment().getPropertySources().addFirst(
                new MapPropertySource("graphTest", Map.of("aio.life.neo4j.enabled", "true")));
        graphContext.registerBean(Driver.class, () -> driver, bean -> bean.setDestroyMethodName(""));
        graphContext.register(Neo4jConfig.class, Neo4jDataAutoConfiguration.class);
        graphContext.refresh();
    }

    @AfterAll
    static void stopNeo4j() {
        if (graphContext != null) graphContext.close();
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
        var persons = new PersonServiceImpl(graphContext.getBean(PersonRepository.class),
                graphContext.getBean(Neo4jTemplate.class), driver);
        relationships = new RelationshipServiceImpl(mock(RelationshipRepository.class), mock(PersonRepository.class), driver);
        mvc = MockMvcBuilders.standaloneSetup(new RelationshipController(persons, relationships))
                .setCustomArgumentResolvers(new CurrentUserIdArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(json)).build();
    }

    @Test
    void 旧学校迁入本科且六个可选字段通过人物接口往返() throws Exception {
        var fields = Map.of(
                "primarySchool", "模拟小学", "juniorHighSchool", "模拟初中", "highSchool", "模拟高中",
                "undergraduateSchool", "模拟本科", "mastersSchool", "模拟硕士", "doctoralSchool", "模拟博士");
        String migration = Files.readString(Path.of("neo4j/migrations/V20261119_01__split_person_school.cypher"));
        try (var session = driver.session()) {
            session.run("""
                    MATCH (source:Person {id:'source'}), (target:Person {id:'target'})
                    SET source.school='原有学校', target.school='另一所学校',
                        target.undergraduateSchool='已填写本科', target.highSchool='已填写高中'
                    """).consume();
            var first = session.run(migration).single();
            assertEquals(1, first.get("migrated").asInt());
            assertEquals(1, first.get("conflicts").asInt());
            var second = session.run(migration).single();
            assertEquals(0, second.get("migrated").asInt());
            assertEquals(1, second.get("conflicts").asInt());
            var stored = session.run("""
                    MATCH (source:Person {id:'source'}), (target:Person {id:'target'})
                    RETURN source.school AS legacy, target.school AS conflict,
                           target.undergraduateSchool AS bachelor, target.highSchool AS highSchool
                    """).single();
            assertTrue(stored.get("legacy").isNull());
            assertEquals("另一所学校", stored.get("conflict").asString());
            assertEquals("已填写本科", stored.get("bachelor").asString());
            assertEquals("已填写高中", stored.get("highSchool").asString());
        }

        try (var auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsLong).thenReturn(1L);
            auth.when(StpUtil::getLoginIdAsString).thenReturn("1");
            var detail = json.readTree(mvc.perform(get("/relationships/persons/source"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
            assertEquals("原有学校", detail.path("undergraduateSchool").asText());
            assertFalse(detail.has("school"));

            var request = json.createObjectNode().put("name", "测试人物");
            fields.forEach(request::put);
            var created = json.readTree(mvc.perform(post("/relationships/persons")
                            .contentType(MediaType.APPLICATION_JSON).content(request.toString()))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
            assertFalse(created.path("id").asText().isBlank());
            String url = "/relationships/persons/" + created.path("id").asText();
            var saved = json.readTree(mvc.perform(get(url)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString()).path("data");
            fields.forEach((key, value) -> assertEquals(value, saved.path(key).asText(), key));

            request.put("doctoralSchool", "");
            request.put("mastersSchool", " 更新后的硕士学校 ");
            mvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).content(request.toString()))
                    .andExpect(status().isOk());
            var updated = json.readTree(mvc.perform(get(url)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString()).path("data");
            assertTrue(updated.path("doctoralSchool").isNull());
            assertEquals("更新后的硕士学校", updated.path("mastersSchool").asText());
            assertEquals("模拟本科", updated.path("undergraduateSchool").asText());

            var exports = new RelationshipDatasets(graphContext.getBeanProvider(Driver.class), true);
            var exported = exports.datasets().getFirst().readBatch(1L, "", 10).stream()
                    .filter(row -> created.path("id").asText().equals(row.get("id")))
                    .findFirst().orElseThrow();
            assertEquals("模拟本科", exported.get("undergraduateSchool"));
            assertEquals("更新后的硕士学校", exported.get("mastersSchool"));
            assertNull(exported.get("doctoralSchool"));
            assertFalse(exported.containsKey("school"));
        }
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
