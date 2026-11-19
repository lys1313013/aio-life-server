package top.aiolife.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface QuerySchemaCatalogMapper {

    record CatalogColumn(String tableName, String tableComment, String columnName, String formattedType,
                         String typeName, String columnComment, boolean nullable, boolean primaryKey) {}

    // 计算列返回 0/1，以便使用项目统一的 NumericBooleanTypeHandler。
    @Select("""
            SELECT c.relname AS "tableName", pg_catalog.obj_description(c.oid, 'pg_class') AS "tableComment",
                   a.attname AS "columnName", pg_catalog.format_type(a.atttypid, a.atttypmod) AS "formattedType",
                   a.atttypid::pg_catalog.regtype::text AS "typeName",
                   pg_catalog.col_description(c.oid, a.attnum) AS "columnComment",
                   (NOT a.attnotnull)::integer AS nullable,
                   (EXISTS (
                       SELECT 1 FROM pg_catalog.pg_index i
                       WHERE i.indrelid = c.oid AND i.indisprimary AND a.attnum = ANY(i.indkey)
                   ))::integer AS "primaryKey"
            FROM pg_catalog.pg_class c
            JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
            JOIN pg_catalog.pg_attribute a ON a.attrelid = c.oid AND a.attnum > 0 AND NOT a.attisdropped
            WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p') AND NOT c.relispartition
              AND c.relname NOT IN ('flyway_schema_history', 'query_schema_relation')
            ORDER BY c.relname, a.attnum
            """)
    List<CatalogColumn> listBusinessColumns();
}
