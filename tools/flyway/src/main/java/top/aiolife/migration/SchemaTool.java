package top.aiolife.migration;

import java.nio.file.Path;
import java.sql.DriverManager;

/** 只读预检入口；迁移/登记直接使用 Maven Flyway 插件。 */
public final class SchemaTool {
    private SchemaTool() {}
    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !args[0].equals("preflight"))
            throw new IllegalArgumentException("Usage: compile exec:java -Dexec.args=preflight -Dschema.contract=<file>");
        String file = System.getProperty("schema.contract");
        if (file == null || file.isBlank()) throw new IllegalArgumentException("schema.contract is required");
        var contract = SchemaContract.read(Path.of(file), MigrationProject.root());
        try (var connection = DriverManager.getConnection(required("FLYWAY_URL"), required("FLYWAY_USER"), required("FLYWAY_PASSWORD"))) {
            connection.setReadOnly(true);
            contract.check(connection, Boolean.getBoolean("schema.acceptMissingChecks"));
            System.out.println("Schema preflight passed for version " + contract.schemaVersion());
        }
    }
    private static String required(String name) {
        var value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing environment variable " + name);
        return value;
    }
}
