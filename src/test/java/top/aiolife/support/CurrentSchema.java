package top.aiolife.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/** 当前 PostgreSQL 结构；H2 只用于局部单元夹具，数据库兼容性由真实 PostgreSQL 集成测试验证。 */
public final class CurrentSchema {
    private CurrentSchema() {}
    public static String table(String name) throws IOException {
        String source = Files.readString(Path.of("sql/migrations/postgresql/V20261110_01__postgresql_schema.sql"));
        var start = Pattern.compile("(?m)^CREATE TABLE \"" + Pattern.quote(name) + "\" \\(").matcher(source);
        if (!start.find()) throw new IllegalArgumentException("当前结构缺少表：" + name);
        int end = source.indexOf("\n );", start.end());
        if (end < 0) throw new IllegalStateException("表定义没有结束：" + name);
        return source.substring(start.start(), end + 4);
    }
    public static String h2Table(String name) throws IOException {
        return table(name).replace(" COLLATE aio_unicode_ci", "").replace(") STORED", ")");
    }
}
