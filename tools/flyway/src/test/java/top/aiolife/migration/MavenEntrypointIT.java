package top.aiolife.migration;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** 真正调用交付的 Maven 入口，防止只测试 Flyway API 而遗漏插件类路径和参数问题。 */
class MavenEntrypointIT {
    @ParameterizedTest @ValueSource(strings = {"mysql:8.0.40", "mysql:8.4.2"})
    void loadsCallbackAndRunsReadOnlyPreflight(String image) throws Exception {
        try (var mysql = new IsolatedMysql(image)) {
            String db = mysql.database();
            assertEquals(0, maven(mysql, db, "compile", "flyway:migrate", "flyway:validate"));
            Path contract = Files.createTempFile("aio-maven-contract-", ".json");
            try {
                try (var connection = mysql.connect(db)) {
                    SchemaContract.write(contract, MigrationProject.root(), mysql.flyway(db).info().current().getVersion().toString(), connection);
                }
                var before = mysql.snapshot(db);
                assertEquals(0, maven(mysql, db, "compile", "exec:java", "-Dexec.args=preflight", "-Dschema.contract=" + contract));
                assertEquals(before, mysql.snapshot(db));
                mysql.sql(db, "DROP TABLE flyway_schema_history");
                assertNotEquals(0, maven(mysql, db, "compile", "flyway:baseline"));
                assertEquals("0", mysql.scalar(db, "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='flyway_schema_history'"));
            } finally { Files.deleteIfExists(contract); }
        }
    }
    private int maven(IsolatedMysql mysql, String db, String... args) throws Exception {
        List<String> command = new ArrayList<>(List.of(Path.of(System.getProperty("migration.mavenHome"), "bin/mvn").toString(), "-B", "-ntp", "-f", "tools/flyway/pom.xml"));
        command.addAll(List.of(args));
        Path output = Files.createTempFile("aio-maven-smoke-", ".log");
        try {
            var builder = new ProcessBuilder(command).directory(MigrationProject.root().toFile()).redirectErrorStream(true).redirectOutput(output.toFile());
            builder.environment().put("FLYWAY_URL", mysql.url(db));
            builder.environment().put("FLYWAY_USER", "root");
            builder.environment().put("FLYWAY_PASSWORD", mysql.password);
            var process = builder.start();
            if (!process.waitFor(90, TimeUnit.SECONDS)) { process.destroyForcibly(); fail("Maven entrypoint timed out"); }
            // 保留错误定位所需输出；测试密码只在环境中传递，不打印环境。
            System.out.println(Files.readString(output));
            return process.exitValue();
        } finally { Files.deleteIfExists(output); }
    }
}
