package top.aiolife.migration;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.Set;

/** 结构只读预检及显式一次性种子拆分登记；日常迁移使用 Maven Flyway 插件。 */
public final class SchemaTool {
    private SchemaTool() {}
    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !Set.of("preflight", "preflight-seed-split", "adopt-seed-split").contains(args[0]))
            throw new IllegalArgumentException("Usage: compile exec:java -Dexec.args=<preflight|preflight-seed-split|adopt-seed-split> -Dschema.contract=<file>");
        String file = System.getProperty("schema.contract");
        if (file == null || file.isBlank()) throw new IllegalArgumentException("schema.contract is required");
        try (var connection = DriverManager.getConnection(required("FLYWAY_URL"), required("FLYWAY_USER"), required("FLYWAY_PASSWORD"))) {
            if (!args[0].equals("preflight")) {
                String backup = System.getProperty("seed.split.backup");
                System.out.println(SeedSplitAdoption.run(connection, MigrationProject.root(), Path.of(file),
                        backup == null ? null : Path.of(backup), args[0].equals("adopt-seed-split")));
                return;
            }
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
