package top.aiolife.migration;

import java.nio.file.Path;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.flywaydb.core.api.configuration.Configuration;

/** Flyway 标准回调：登记前检查真实结构，迁移顺序和历史仍由 Flyway 管理。 */
public final class MigrationGuard implements Callback {
    public boolean supports(Event event, Context context) {
        return event == Event.BEFORE_BASELINE || event == Event.BEFORE_VALIDATE || event == Event.BEFORE_MIGRATE || event == Event.BEFORE_CLEAN;
    }
    public boolean canHandleInTransaction(Event event, Context context) { return false; }
    public String getCallbackName() { return "aio-schema-preflight"; }
    static void checkConfiguration(Configuration config) {
        if (config.isBaselineOnMigrate() || config.isSkipExecutingMigrations() || config.isOutOfOrder()
                || !config.isCleanDisabled() || !config.isValidateOnMigrate() || !config.isValidateMigrationNaming()
                || !SchemaSnapshot.HISTORY.equals(config.getTable()))
            throw new IllegalArgumentException("Unsafe Flyway configuration: auto baseline, skip, clean and out-of-order are disabled; validation and standard history are required");
    }
    public void handle(Event event, Context context) {
        try {
            if (event == Event.BEFORE_CLEAN) throw new IllegalArgumentException("clean is disabled");
            var config = context.getConfiguration();
            checkConfiguration(config);
            var root = MigrationProject.root(config);
            var connection = context.getConnection();
            SchemaSnapshot.version(connection); // 两条 MySQL 系列均在执行任何 DDL 前检查版本下限。
            if (event == Event.BEFORE_BASELINE) {
                String file = System.getProperty("schema.contract");
                if (file == null || file.isBlank()) throw new IllegalArgumentException("baseline requires -Dschema.contract=<adoption-contract.json>");
                var contract = SchemaContract.read(Path.of(file), root);
                if (!org.flywaydb.core.api.MigrationVersion.fromVersion(contract.schemaVersion()).equals(config.getBaselineVersion()))
                    throw new IllegalArgumentException("Contract version differs from baselineVersion");
                contract.check(connection, Boolean.getBoolean("schema.acceptMissingChecks"));
            } else {
                var tables = SchemaSnapshot.rows(connection, "SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE()");
                boolean history = tables.stream().anyMatch(row -> SchemaSnapshot.HISTORY.equals(row.getFirst()));
                if (!history && !tables.isEmpty()) throw new IllegalArgumentException("Nonempty database requires verified explicit baseline");
            }
        } catch (Exception error) {
            throw new FlywayException("Migration guard: " + error.getMessage(), error);
        }
    }
}
