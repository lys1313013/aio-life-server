package top.aiolife.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** 测试直接使用当前全量结构，不依赖已删除的增量脚本。 */
public final class CurrentSchema {
    private static final Path SOURCE = Path.of("sql/1_init_table/2026-08-18_init_all_tables.sql");

    private CurrentSchema() {}

    public static String table(String name) throws IOException {
        String schema = Files.readString(SOURCE);
        int start = schema.indexOf("CREATE TABLE IF NOT EXISTS `" + name + "`");
        if (start < 0) throw new IllegalArgumentException("当前结构缺少表：" + name);
        return schema.substring(start, schema.indexOf(';', start));
    }

    /** H2 的 MySQL 模式保留字段、索引及约束，仅移除引擎和排序规则等表选项。 */
    public static String h2Table(String name) throws IOException {
        String sql = table(name);
        return sql.substring(0, sql.indexOf(" ENGINE="));
    }
}
