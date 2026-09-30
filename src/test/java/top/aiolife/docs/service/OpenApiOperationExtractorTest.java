package top.aiolife.docs.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import top.aiolife.docs.api.DocumentationController;

import static org.junit.jupiter.api.Assertions.*;

class OpenApiOperationExtractorTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void 保留传递引用循环多态与路径参数并剔除无关定义() throws Exception {
        ObjectNode source = (ObjectNode) mapper.readTree("""
                {
                  "openapi":"3.0.1", "info":{"title":"test","version":"1"},
                  "security":[{"bearer":[]}],
                  "paths":{
                    "/pets/{id}":{
                      "parameters":[{"$ref":"#/components/parameters/id"}],
                      "get":{"operationId":"get_pet","responses":{"200":{"$ref":"#/components/responses/pet"}}},
                      "delete":{"operationId":"delete_pet"}
                    },
                    "/unrelated":{"get":{"operationId":"other"}}
                  },
                  "components":{
                    "securitySchemes":{"bearer":{"type":"http","scheme":"bearer"},"unused":{"type":"http"}},
                    "parameters":{"id":{"name":"id","in":"path","required":true,"schema":{"$ref":"#/components/schemas/ID"}}},
                    "responses":{"pet":{"description":"ok","content":{"application/json":{"schema":{"$ref":"#/components/schemas/Pet"}}}}},
                    "schemas":{
                      "ID":{"type":"string"},
                      "Pet":{"type":"object","discriminator":{"propertyName":"type","mapping":{"dog":"Dog","cat":"#/components/schemas/Cat"}},"properties":{"children":{"type":"array","items":{"$ref":"#/components/schemas/Pet"}}}},
                      "Dog":{"allOf":[{"$ref":"#/components/schemas/Pet"},{"$ref":"#/components/schemas/A~1B~0C"}]},
                      "Cat":{"type":"object"},"A/B~C":{"type":"object"},"Unused":{"type":"string"}
                    }
                  }
                }
                """);
        ObjectNode before = source.deepCopy();
        ObjectNode result = OpenApiOperationExtractor.extract(source, "/pets/{id}", "GET");
        assertEquals(1, result.path("paths").size());
        assertFalse(result.at("/paths/~1pets~1{id}").has("delete"));
        assertEquals(5, result.at("/components/schemas").size());
        assertFalse(result.at("/components/schemas").has("Unused"));
        assertTrue(result.at("/components/schemas").has("A/B~C"));
        assertTrue(result.at("/components/parameters").has("id"));
        assertTrue(result.at("/components/responses").has("pet"));
        assertEquals(1, result.at("/components/securitySchemes").size());
        assertEquals(before, source);
        ((ObjectNode) result.at("/components/schemas/Pet")).put("description", "mutated");
        assertEquals(before, source);
        assertFalse(OpenApiOperationExtractor.extract(source, "/pets/{id}", "get").at("/components/schemas/Pet").has("description"));
    }

    @Test
    void 显式匿名覆盖全局鉴权且不携带无关组件() throws Exception {
        ObjectNode source = (ObjectNode) mapper.readTree("""
                {"openapi":"3.0.1","security":[{"bearer":[]}],"paths":{"/login":{"post":{"security":[],"responses":{"200":{"description":"ok"}}}}},
                 "components":{"securitySchemes":{"bearer":{"type":"http","scheme":"bearer"}}}}
                """);
        ObjectNode result = OpenApiOperationExtractor.extract(source, "/login", "post");
        assertTrue(result.path("security").isEmpty());
        assertFalse(result.has("components"));
    }

    @Test
    void 缺失引用明确失败而不返回残缺文档() throws Exception {
        ObjectNode source = (ObjectNode) mapper.readTree("""
                {"paths":{"/test":{"get":{"responses":{"200":{"$ref":"#/components/responses/missing"}}}}}}
                """);
        assertThrows(IllegalStateException.class, () -> OpenApiOperationExtractor.extract(source, "/test", "get"));
        assertThrows(IllegalArgumentException.class, () -> OpenApiOperationExtractor.extract(source, "/test", "post"));
    }

    @Test
    void 关闭Springdoc同时关闭渐进文档接口() {
        new ApplicationContextRunner().withPropertyValues("springdoc.api-docs.enabled=false")
                .withUserConfiguration(DocumentationController.class, DocumentationService.class)
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertEquals(0, context.getBeansOfType(DocumentationController.class).size());
                    assertEquals(0, context.getBeansOfType(DocumentationService.class).size());
                });
    }
}
