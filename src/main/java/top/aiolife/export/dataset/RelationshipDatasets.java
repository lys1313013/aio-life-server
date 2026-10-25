package top.aiolife.export.dataset;

import org.neo4j.driver.Driver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 可选数据源独立注册。节点、关系及两端节点都必须属于同一用户。 */
@Component
public class RelationshipDatasets implements ExportDatasetContributor {
    private final ObjectProvider<Driver> drivers;
    private final boolean enabled;

    public RelationshipDatasets(
            ObjectProvider<Driver> drivers,
            @Value("${aio.life.neo4j.enabled:false}") boolean enabled) {
        this.drivers = drivers;
        this.enabled = enabled;
    }

    public List<ExportDataset> datasets() {
        return List.of(new GraphDataset(false), new GraphDataset(true));
    }

    private final class GraphDataset implements ExportDataset {
        private final boolean edges;

        GraphDataset(boolean edges) {
            this.edges = edges;
        }

        public String key() {
            return edges ? "relationship_edges" : "relationship_nodes";
        }

        public String title() {
            return edges ? "人物关系" : "人物档案";
        }

        public String source() {
            return "NEO4J";
        }

        public boolean available() {
            return enabled;
        }

        public List<String> menus() {
            return List.of("/relationship/graph");
        }

        public List<ExportColumn> columns() {
            String[][] fields =
                    edges
                            ? new String[][] {
                                {"id", "关系ID"},
                                {"sourceId", "源人物ID"},
                                {"targetId", "目标人物ID"},
                                {"relationType", "关系类型"},
                                {"direction", "方向"},
                                {"description", "描述"},
                                {"tags", "标签"},
                                {"createdAt", "创建时间"}
                            }
                            : new String[][] {
                                {"id", "人物ID"},
                                {"name", "姓名"},
                                {"category", "分类"},
                                {"description", "简介"},
                                {"tags", "标签"},
                                {"birthday", "生日"},
                                {"phone", "电话"},
                                {"email", "邮箱"},
                                {"school", "学校"},
                                {"socialLinks", "社交链接"},
                                {"notes", "备注"},
                                {"createdAt", "创建时间"},
                                {"updatedAt", "更新时间"}
                            };
            return Arrays.stream(fields).map(f -> new ExportColumn(f[0], f[1], "TEXT")).toList();
        }

        public List<Map<String, Object>> readBatch(long ownerId, String cursor, int limit) {
            if (!enabled) throw new IllegalStateException("Relationship source disabled");
            if (limit < 1 || limit > 1000) throw new IllegalArgumentException("Invalid batch size");
            Driver driver = drivers.getIfAvailable();
            if (driver == null) throw new IllegalStateException("Relationship source unavailable");
            // Cypher 的结构固定；字段清单只来自以上代码定义，所有运行时值均绑定。
            String query =
                    edges
                            ? "MATCH (a:Person)-[r:RELATES_TO]->(b:Person) WHERE a.userId=$owner"
                                    + " AND r.userId=$owner AND b.userId=$owner AND ($cursor='' OR"
                                    + " elementId(r)>$cursor) RETURN elementId(r) AS id, a.id AS"
                                    + " sourceId, b.id AS targetId, r.relationType AS relationType,"
                                    + " r.direction AS direction, r.description AS description,"
                                    + " r.tags AS tags, r.createdAt AS createdAt ORDER BY id LIMIT"
                                    + " $limit"
                            : "MATCH (p:Person) WHERE p.userId=$owner AND ($cursor='' OR"
                                  + " p.id>$cursor) RETURN p.id AS id, p.name AS name, p.category"
                                  + " AS category, p.description AS description, p.tags AS tags,"
                                  + " p.birthday AS birthday, p.phone AS phone, p.email AS email,"
                                  + " p.school AS school, p.socialLinks AS socialLinks, p.notes AS"
                                  + " notes, p.createdAt AS createdAt, p.updatedAt AS updatedAt"
                                  + " ORDER BY id LIMIT $limit";
            try (var session = driver.session()) {
                return session.executeRead(
                        tx ->
                                tx.run(
                                                query,
                                                Map.of(
                                                        "owner",
                                                        ownerId,
                                                        "cursor",
                                                        cursor == null ? "" : cursor,
                                                        "limit",
                                                        limit))
                                        .list(
                                                record -> {
                                                    Map<String, Object> row = new LinkedHashMap<>();
                                                    for (var column : columns())
                                                        row.put(
                                                                column.key(),
                                                                record.get(column.key()).isNull()
                                                                        ? null
                                                                        : record.get(column.key())
                                                                                .asObject());
                                                    return row;
                                                }));
            }
        }
    }
}
