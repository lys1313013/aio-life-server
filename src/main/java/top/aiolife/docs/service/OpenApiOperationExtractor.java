package top.aiolife.docs.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** 裁剪单接口并保留传递引用；不展开循环 Schema，不修改 springdoc 原文档。 */
public final class OpenApiOperationExtractor {
    private OpenApiOperationExtractor() {}

    public static ObjectNode extract(ObjectNode source, String path, String method) {
        ObjectNode result = source.objectNode();
        for (String field : new String[]{"openapi", "info", "servers", "security", "externalDocs"}) {
            if (source.has(field)) result.set(field, source.get(field).deepCopy());
        }
        JsonNode originalPath = source.path("paths").path(path);
        JsonNode operation = originalPath.path(method.toLowerCase(Locale.ROOT));
        if (operation.isMissingNode()) throw new IllegalArgumentException("接口操作不存在");
        ObjectNode selectedPath = result.putObject("paths").putObject(path);
        for (String field : new String[]{"summary", "description", "parameters", "servers"}) {
            if (originalPath.has(field)) selectedPath.set(field, originalPath.get(field).deepCopy());
        }
        selectedPath.set(method.toLowerCase(Locale.ROOT), operation.deepCopy());
        // 操作显式 security=[] 表示匿名，不能再带上继承的 Bearer 定义。
        if (operation.has("security")) result.set("security", operation.get("security").deepCopy());
        ArrayDeque<String> pending = new ArrayDeque<>();
        collectReferences(result, pending);
        Set<String> visited = new HashSet<>();
        ObjectNode components = result.putObject("components");
        while (!pending.isEmpty()) {
            String reference = pending.removeFirst();
            if (!reference.startsWith("#/components/")) {
                if (reference.startsWith("#/") && result.at(reference.substring(1)).isMissingNode()) {
                    throw new IllegalStateException("单接口文档存在不支持的本地引用：" + reference);
                }
                continue;
            }
            String[] segments = reference.substring(2).split("/", 4);
            if (segments.length < 3 || source.at(reference.substring(1)).isMissingNode()) {
                throw new IllegalStateException("接口文档引用不存在：" + reference);
            }
            String componentPointer = "/components/" + segments[1] + "/" + segments[2];
            if (!visited.add(componentPointer)) continue;
            JsonNode component = source.at(componentPointer).deepCopy();
            String section = unescape(segments[1]);
            String name = unescape(segments[2]);
            ObjectNode sectionNode = components.has(section) ? (ObjectNode) components.get(section) : components.putObject(section);
            sectionNode.set(name, component);
            collectReferences(component, pending);
        }
        if (components.isEmpty()) result.remove("components");
        return result;
    }

    private static void collectReferences(JsonNode node, ArrayDeque<String> pending) {
        if (node.isObject()) {
            if (node.path("$ref").isTextual()) pending.add(node.path("$ref").asText());
            node.path("discriminator").path("mapping").elements().forEachRemaining(value -> {
                if (value.isTextual()) {
                    String reference = value.asText();
                    // OpenAPI 允许 discriminator mapping 使用裸 Schema 名称。
                    pending.add(reference.contains("/") || reference.contains(":") ? reference
                            : "#/components/schemas/" + escape(reference));
                }
            });
            node.path("security").elements().forEachRemaining(requirement -> requirement.fieldNames().forEachRemaining(name ->
                    pending.add("#/components/securitySchemes/" + escape(name))));
        }
        if (node.isContainerNode()) node.elements().forEachRemaining(child -> collectReferences(child, pending));
    }

    private static String escape(String value) { return value.replace("~", "~0").replace("/", "~1"); }
    private static String unescape(String value) { return value.replace("~1", "/").replace("~0", "~"); }
}
