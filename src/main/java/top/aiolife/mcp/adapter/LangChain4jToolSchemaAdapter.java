package top.aiolife.mcp.adapter;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.output.structured.Description;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * LangChain4j 工具 schema 适配器
 *
 * @author Lys
 * @date 2026/04/25
 */
@Component
public class LangChain4jToolSchemaAdapter {

    public McpSchema.Tool toMcpTool(String name,
                                    String description,
                                    Method method,
                                    ToolSpecification toolSpecification) {
        Map<String, Object> schemaMap;
        if (method.getParameterCount() == 0) {
            schemaMap = objectSchema(Map.of(), List.of(), false);
        } else {
            schemaMap = buildSchema(method.getGenericParameterTypes()[0], "");
        }
        McpSchema.JsonSchema inputSchema = new McpSchema.JsonSchema(
                stringValue(schemaMap.get("type")),
                mapValue(schemaMap.get("properties")),
                listValue(schemaMap.get("required")),
                booleanValue(schemaMap.get("additionalProperties")),
                mapValue(schemaMap.get("definitions")),
                mapValue(schemaMap.get("definitions"))
        );
        return new McpSchema.Tool(
                name,
                name,
                description != null ? description : toolSpecification.description(),
                inputSchema,
                null,
                new McpSchema.ToolAnnotations(name, false, false, false, false, null),
                toolSpecification.metadata()
        );
    }

    private Map<String, Object> buildSchema(Type type, String path) {
        if (type instanceof GenericArrayType genericArrayType) {
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "array");
            schema.put("items", buildSchema(genericArrayType.getGenericComponentType(), appendArray(path)));
            return schema;
        }

        Class<?> rawClass = rawClass(type);
        if (rawClass == null) {
            return objectSchema(Map.of(), List.of(), true);
        }
        if (isIntegerType(rawClass)) {
            return primitiveSchema("integer");
        }
        if (isNumberType(rawClass)) {
            return primitiveSchema("number");
        }
        if (isBooleanType(rawClass)) {
            return primitiveSchema("boolean");
        }
        if (isStringLikeType(rawClass)) {
            return primitiveSchema("string");
        }
        if (rawClass.isEnum()) {
            Map<String, Object> schema = primitiveSchema("string");
            List<String> enumValues = new ArrayList<>();
            for (Object enumConstant : rawClass.getEnumConstants()) {
                enumValues.add(((Enum<?>) enumConstant).name());
            }
            schema.put("enum", enumValues);
            return schema;
        }
        if (rawClass.isArray()) {
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "array");
            schema.put("items", buildSchema(rawClass.getComponentType(), appendArray(path)));
            return schema;
        }
        if (Collection.class.isAssignableFrom(rawClass)) {
            Type itemType = Object.class;
            if (type instanceof ParameterizedType parameterizedType && parameterizedType.getActualTypeArguments().length > 0) {
                itemType = parameterizedType.getActualTypeArguments()[0];
            }
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "array");
            schema.put("items", buildSchema(itemType, appendArray(path)));
            return schema;
        }
        if (Map.class.isAssignableFrom(rawClass) || Object.class.equals(rawClass)) {
            return objectSchema(Map.of(), List.of(), true);
        }

        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (Field field : collectFields(rawClass)) {
            String fieldPath = path.isEmpty() ? field.getName() : path + "." + field.getName();
            Map<String, Object> fieldSchema = buildSchema(field.getGenericType(), fieldPath);
            Description desc = field.getAnnotation(Description.class);
            if (desc != null && desc.value().length > 0) {
                fieldSchema.put("description", String.join("\n", desc.value()));
            }
            properties.put(field.getName(), fieldSchema);
            if (field.getType().isPrimitive()) {
                required.add(field.getName());
            }
        }
        return objectSchema(properties, required, false);
    }

    private List<Field> collectFields(Class<?> type) {
        LinkedHashMap<String, Field> fields = new LinkedHashMap<>();
        List<Class<?>> hierarchy = new ArrayList<>();
        Class<?> current = type;
        while (current != null && !Object.class.equals(current)) {
            hierarchy.add(0, current);
            current = current.getSuperclass();
        }
        for (Class<?> hierarchyType : hierarchy) {
            for (Field field : hierarchyType.getDeclaredFields()) {
                if (field.isSynthetic() || Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                fields.putIfAbsent(field.getName(), field);
            }
        }
        return new ArrayList<>(fields.values());
    }

    private Map<String, Object> primitiveSchema(String type) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", type);
        return schema;
    }

    private Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required, boolean additionalProperties) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        schema.put("additionalProperties", additionalProperties);
        schema.put("definitions", Map.of());
        return schema;
    }

    private String appendArray(String path) {
        return path.isEmpty() ? "[]" : path + "[]";
    }

    private Class<?> rawClass(Type type) {
        if (type instanceof Class<?> clazz) {
            return clazz;
        }
        if (type instanceof ParameterizedType parameterizedType) {
            Type rawType = parameterizedType.getRawType();
            if (rawType instanceof Class<?> clazz) {
                return clazz;
            }
        }
        return null;
    }

    private boolean isIntegerType(Class<?> type) {
        return type == byte.class
                || type == short.class
                || type == int.class
                || type == long.class
                || type == Byte.class
                || type == Short.class
                || type == Integer.class
                || type == Long.class;
    }

    private boolean isNumberType(Class<?> type) {
        return type == float.class
                || type == double.class
                || type == Float.class
                || type == Double.class
                || Number.class.isAssignableFrom(type);
    }

    private boolean isBooleanType(Class<?> type) {
        return type == boolean.class || type == Boolean.class;
    }

    private boolean isStringLikeType(Class<?> type) {
        return CharSequence.class.isAssignableFrom(type)
                || Date.class.isAssignableFrom(type)
                || LocalDate.class.isAssignableFrom(type)
                || LocalTime.class.isAssignableFrom(type)
                || LocalDateTime.class.isAssignableFrom(type)
                || OffsetDateTime.class.isAssignableFrom(type)
                || ZonedDateTime.class.isAssignableFrom(type)
                || Temporal.class.isAssignableFrom(type);
    }

    private Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, mapValue) -> result.put(String.valueOf(key), mapValue));
            return result;
        }
        return Map.of();
    }

    private List<String> listValue(Object value) {
        if (value instanceof Collection<?> collection) {
            List<String> result = new ArrayList<>();
            for (Object item : collection) {
                result.add(String.valueOf(item));
            }
            return result;
        }
        return List.of();
    }

    private Boolean booleanValue(Object value) {
        return value instanceof Boolean bool ? bool : null;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
