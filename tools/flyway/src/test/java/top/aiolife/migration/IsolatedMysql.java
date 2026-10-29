package top.aiolife.migration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/** 只接受镜像名，连接信息来自本测试创建的容器，不读取业务环境变量。 */
final class IsolatedMysql implements AutoCloseable {
    final String password = UUID.randomUUID().toString();
    final GenericContainer<?> container;
    final Path root;
    IsolatedMysql(String image) {
        this(image, MigrationProject.root());
    }
    IsolatedMysql(String image, Path root) {
        this.root = root;
        container = new GenericContainer<>(image).withEnv("MYSQL_ROOT_PASSWORD", password)
                .withEnv("MYSQL_ROOT_HOST", "%").withExposedPorts(3306)
                .waitingFor(Wait.forListeningPort()).withStartupTimeout(Duration.ofMinutes(3));
        container.start();
    }
    String url(String db) {
        return "jdbc:mariadb://" + container.getHost() + ":" + container.getMappedPort(3306) + "/" + db
                + "?sslMode=disable&allowPublicKeyRetrieval=true&allowMultiQueries=true";
    }
    Connection connect(String db) throws Exception { return DriverManager.getConnection(url(db), "root", password); }
    String database() throws Exception {
        String db = "fixture_" + UUID.randomUUID().toString().replace("-", "");
        sql("mysql", "CREATE DATABASE `" + db + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        return db;
    }
    void sql(String db, String sql) throws Exception {
        try (var connection = connect(db); var statement = connection.createStatement()) {
            statement.execute(sql);
            while (statement.getMoreResults() || statement.getUpdateCount() != -1) { /* Drain multi-statements. */ }
        }
    }
    List<List<String>> rows(String db, String sql) throws Exception {
        try (var connection = connect(db)) { return SchemaSnapshot.rows(connection, sql); }
    }
    String scalar(String db, String sql) throws Exception { return rows(db, sql).getFirst().getFirst(); }
    SchemaSnapshot snapshot(String db) throws Exception { try (var connection = connect(db)) { return SchemaSnapshot.read(connection); } }
    FluentConfiguration configuration(String db) {
        return Flyway.configure().dataSource(url(db), "root", password)
                .locations("filesystem:" + root.resolve("sql/migrations"))
                .cleanDisabled(true).createSchemas(false).baselineOnMigrate(false).baselineVersion(System.getProperty("adoption.version"))
                .validateMigrationNaming(true).validateOnMigrate(true).placeholderReplacement(false).callbacks(new MigrationGuard());
    }
    Flyway flyway(String db) { return configuration(db).load(); }
    String source(String file) throws Exception { return Files.readString(root.resolve("sql/migrations/" + file)); }
    void baselineFixture(String db) throws Exception {
        script(db, source("B20261028_00_001__mysql_compatible_baseline.sql"));
    }
    void migration(String db, String filename) throws Exception { script(db, source(filename)); }
    void script(String db, String sql) throws Exception {
        Path directory = Files.createTempDirectory("aio-migration-fixture-");
        Path script = directory.resolve("V1__fixture.sql");
        try {
            Files.writeString(script, sql);
            // 仅在本类自建数据库中用于可重复执行单个 SQL；生产入口不开放自动 baseline。
            Flyway.configure().dataSource(url(db), "root", password).locations("filesystem:" + directory)
                    .baselineOnMigrate(true).baselineVersion("0").placeholderReplacement(false).load().migrate();
        } finally {
            sql(db, "DROP TABLE IF EXISTS flyway_schema_history");
            Files.deleteIfExists(script); Files.deleteIfExists(directory);
        }
    }
    Map<String, List<String>> data(String db) throws Exception {
        Map<String, List<String>> result = new TreeMap<>();
        var schema = snapshot(db);
        for (var table : schema.sections().get("tables")) {
            List<String> rows = new ArrayList<>();
            String columns = String.join(",", schema.sections().get("columns").stream().filter(c -> c.getFirst().equals(table.getFirst())).map(c -> "`" + c.get(1) + "`").toList());
            for (var row : rows(db, "SELECT " + columns + " FROM `" + table.getFirst() + "`")) rows.add(SchemaContract.JSON.writeValueAsString(row));
            Collections.sort(rows); result.put(table.getFirst(), rows);
        }
        return result;
    }
    public void close() { container.close(); }
}
