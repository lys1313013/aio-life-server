package top.aiolife.system.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import top.aiolife.system.mapper.QuerySchemaCatalogMapper;
import top.aiolife.system.mapper.QuerySchemaCatalogMapper.CatalogColumn;
import top.aiolife.system.mapper.QuerySchemaRelationMapper;
import top.aiolife.system.pojo.entity.QuerySchemaRelationEntity;
import top.aiolife.system.pojo.req.SchemaColumnMapping;
import top.aiolife.system.pojo.req.SchemaRelationCondition;
import top.aiolife.system.pojo.req.SchemaRelationSaveReq;
import top.aiolife.system.pojo.vo.QuerySchemaVO;
import top.aiolife.system.pojo.vo.SchemaColumnVO;
import top.aiolife.system.pojo.vo.SchemaRelationVO;
import top.aiolife.system.pojo.vo.SchemaTableVO;

@Service
@RequiredArgsConstructor
public class QuerySchemaService {
    private final QuerySchemaCatalogMapper catalogMapper;
    private final QuerySchemaRelationMapper relationMapper;
    private final ObjectMapper json;

    @Transactional(readOnly = true)
    public QuerySchemaVO snapshot() {
        var catalog = readCatalog();
        var relations = relationMapper.selectList(Wrappers.<QuerySchemaRelationEntity>lambdaQuery()
                .orderByAsc(QuerySchemaRelationEntity::getSourceTable, QuerySchemaRelationEntity::getRelationKey));
        return new QuerySchemaVO("public", catalog.values().stream().map(columns -> {
            var first = columns.values().iterator().next();
            return new SchemaTableVO(first.tableName(), first.tableComment(), columns.values().stream()
                    .map(column -> new SchemaColumnVO(column.columnName(), column.formattedType(),
                            column.columnComment(), column.nullable(), column.primaryKey())).toList());
        }).toList(), relations.stream().map(entity -> {
            SchemaRelationSaveReq value = deserialize(entity);
            return toVO(entity.getId(), value, invalidReason(value, catalog));
        }).toList());
    }

    @Transactional
    public SchemaRelationVO create(SchemaRelationSaveReq request, long userId) {
        validate(request);
        var entity = new QuerySchemaRelationEntity();
        copy(request, entity);
        entity.fillCreateCommonField(userId);
        try {
            relationMapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw duplicateKey(exception);
        }
        return toVO(entity.getId(), deserialize(entity), null);
    }

    @Transactional
    public SchemaRelationVO update(long id, SchemaRelationSaveReq request, long userId) {
        validate(request);
        var entity = new QuerySchemaRelationEntity();
        entity.setId(id);
        copy(request, entity);
        entity.fillUpdateCommonField(userId);
        try {
            if (relationMapper.updateById(entity) != 1) throw notFound();
        } catch (DuplicateKeyException exception) {
            throw duplicateKey(exception);
        }
        return toVO(id, deserialize(entity), null);
    }

    @Transactional
    public void delete(long id, long userId) {
        var update = Wrappers.<QuerySchemaRelationEntity>lambdaUpdate()
                .eq(QuerySchemaRelationEntity::getId, id)
                .set(QuerySchemaRelationEntity::getIsDeleted, 1)
                .set(QuerySchemaRelationEntity::getUpdateUser, userId)
                .set(QuerySchemaRelationEntity::getUpdateTime, LocalDateTime.now());
        if (relationMapper.update(update) != 1) throw notFound();
    }

    private Map<String, Map<String, CatalogColumn>> readCatalog() {
        Map<String, Map<String, CatalogColumn>> tables = new LinkedHashMap<>();
        for (CatalogColumn column : catalogMapper.listBusinessColumns()) {
            tables.computeIfAbsent(column.tableName(), ignored -> new LinkedHashMap<>())
                    .put(column.columnName(), column);
        }
        return tables;
    }

    private void validate(SchemaRelationSaveReq request) {
        String reason = invalidReason(request, readCatalog());
        if (reason != null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }

    private static String invalidReason(SchemaRelationSaveReq value,
                                        Map<String, Map<String, CatalogColumn>> catalog) {
        var source = catalog.get(value.sourceTable());
        var target = catalog.get(value.targetTable());
        if (source == null || target == null) return "来源表或目标表不存在";
        if ("owner".equals(value.relationKind()) && !("sys_user".equals(value.targetTable())
                && value.columnMapping().size() == 1 && "id".equals(value.columnMapping().getFirst().targetColumn()))) {
            return "用户归属关系须指向 sys_user.id";
        }
        if ("polymorphic".equals(value.relationKind()) && value.conditions().stream()
                .noneMatch(condition -> "source".equals(condition.side()) && "eq".equals(condition.operator()))) {
            return "多态关联须包含来源表的等值判别条件";
        }
        Set<String> sourceColumns = new HashSet<>();
        Set<String> targetColumns = new HashSet<>();
        for (SchemaColumnMapping mapping : value.columnMapping()) {
            var from = source.get(mapping.sourceColumn());
            var to = target.get(mapping.targetColumn());
            if (from == null || to == null) return "映射字段已不存在：" + mapping.sourceColumn() + " → " + mapping.targetColumn();
            if (!from.typeName().equals(to.typeName())) return "映射字段类型不同：" + from.formattedType() + " → " + to.formattedType();
            if (!sourceColumns.add(from.columnName()) || !targetColumns.add(to.columnName())) return "同侧映射字段不可重复";
        }
        for (SchemaRelationCondition condition : value.conditions()) {
            var columns = switch (condition.side()) {
                case "source" -> source;
                case "target" -> target;
                default -> Map.<String, CatalogColumn>of();
            };
            if (!columns.containsKey(condition.column())) return "条件字段已不存在：" + condition.column();
            if ("eq".equals(condition.operator()) != (condition.value() != null)) {
                return "等值条件必须提供值，空值判断不填写值";
            }
        }
        return null;
    }

    private void copy(SchemaRelationSaveReq value, QuerySchemaRelationEntity entity) {
        entity.setSourceTable(value.sourceTable());
        entity.setTargetTable(value.targetTable());
        entity.setRelationKey(value.relationKey());
        entity.setDisplayName(value.displayName().strip());
        entity.setRelationKind(value.relationKind());
        entity.setCardinality(value.cardinality());
        entity.setDescription(Objects.requireNonNullElse(value.description(), "").strip());
        try {
            entity.setColumnMapping(json.writeValueAsString(value.columnMapping()));
            entity.setConditions(json.writeValueAsString(value.conditions()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法编码关系配置", exception);
        }
    }

    private SchemaRelationSaveReq deserialize(QuerySchemaRelationEntity entity) {
        try {
            return new SchemaRelationSaveReq(entity.getSourceTable(), entity.getTargetTable(),
                    entity.getRelationKey(), entity.getDisplayName(), entity.getRelationKind(), entity.getCardinality(),
                    json.readValue(entity.getColumnMapping(), new TypeReference<List<SchemaColumnMapping>>() {}),
                    json.readValue(entity.getConditions(), new TypeReference<List<SchemaRelationCondition>>() {}),
                    entity.getDescription());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法读取关系配置 " + entity.getId(), exception);
        }
    }

    private static SchemaRelationVO toVO(long id, SchemaRelationSaveReq value, String invalidReason) {
        return new SchemaRelationVO(id, value.sourceTable(), value.targetTable(), value.relationKey(),
                value.displayName(), value.relationKind(), value.cardinality(), value.columnMapping(),
                value.conditions(), value.description(), invalidReason == null, invalidReason);
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "逻辑关系已不存在");
    }

    private static ResponseStatusException duplicateKey(DuplicateKeyException exception) {
        return new ResponseStatusException(HttpStatus.CONFLICT, "来源表下已存在相同的关系标识", exception);
    }
}
