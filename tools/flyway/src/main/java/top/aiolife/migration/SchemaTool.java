package top.aiolife.migration;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.Set;

/** 结构只读预检；日常迁移使用 Maven Flyway 插件。 */
public final class SchemaTool {
    private SchemaTool() {}
    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !Set.of("preflight").contains(args[0]))
            throw new IllegalArgumentException("Usage: compile exec:java -Dexec.args=<preflight> -Dschema.contract=<file>");
        String file = System.getProperty("schema.contract");
        if (file == null || file.isBlank()) throw new IllegalArgumentException("schema.contract is required");
        try (var connection = DriverManager.getConnection(required("FLYWAY_URL"), required("FLYWAY_USER"), required("FLYWAY_PASSWORD"))) {
            var contract = SchemaContract.read(Path.of(file), MigrationProject.root());
            connection.setReadOnly(true);
            contract.check(connection);
            System.out.println("Schema preflight passed for version " + contract.schemaVersion());
        }
    }
    private static String required(String name) {
        var value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing environment variable " + name);
        return value;
    }
}
