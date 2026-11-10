package top.aiolife.migration;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.Connection;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.internal.resolver.ChecksumCalculator;
import org.flywaydb.core.internal.resource.StringResource;

/** 一次性拆分登记：只转换已校验的旧种子账本，不执行种子 SQL，不更新业务数据。 */
public final class SeedSplitAdoption {
    private static final String ORIGINAL_SHA256 = "c4448b802bdb1dd95c3598c90277957cb126f95d18284c333fabf52dfff6f6d3";
    private static final String MARKER = "-- BEGIN ORIGINAL SEED SQL\n";
    public record SeedFile(String filename, String sha256) {}
    public record Manifest(String original, String originalSha256, String schemaSha256, List<SeedFile> files, String originalHeader) {}
    public record Entry(int rank, String version, String description, String type, String script,
                        Integer checksum, String installedBy, Timestamp installedOn, int executionTime, boolean success) {}
    public record Migration(String version, String filename, String description, int checksum) {}
    public record Sources(Manifest manifest, int originalChecksum, List<Migration> migrations, String originalSql) {}
    private SeedSplitAdoption() {}

    public static int checksum(String sql) {
        // 与工具固定的 Flyway 版本使用同一算法，避免自行实现换行/BOM 规则。
        return ChecksumCalculator.calculate(new StringResource(sql));
    }

    static Sources sources(Path root) throws Exception {
        Path archive = root.resolve("sql/reference/seed-split");
        var manifest = SchemaContract.JSON.readValue(archive.resolve("seed-split.json").toFile(), Manifest.class);
        if (!manifest.original().equals("V20261110_02__public_seed_data.sql")
                || !manifest.originalSha256().equals(ORIGINAL_SHA256) || manifest.files().size() != 11)
            throw new IllegalArgumentException("Unknown seed split manifest");
        Path directory = root.resolve("sql/migrations");
        if (!MigrationProject.sha256(Files.readAllBytes(directory.resolve("V20261110_01__postgresql_schema.sql")))
                .equals(manifest.schemaSha256())) throw new IllegalArgumentException("Schema source checksum differs");
        StringBuilder reconstructed = new StringBuilder(manifest.originalHeader());
        List<Migration> migrations = new ArrayList<>();
        for (int i = 0; i < manifest.files().size(); i++) {
            var file = manifest.files().get(i);
            String prefix = "V20261110_02_%03d__".formatted(i + 1);
            if (!file.filename().matches(prefix + "[a-z0-9_]+\\.sql"))
                throw new IllegalArgumentException("Unexpected seed migration filename");
            String sql = Files.readString(directory.resolve(file.filename()));
            if (!MigrationProject.sha256(sql.getBytes(StandardCharsets.UTF_8)).equals(file.sha256()))
                throw new IllegalArgumentException("Split seed checksum differs: " + file.filename());
            int start = sql.indexOf(MARKER);
            if (start < 0) throw new IllegalArgumentException("Missing original SQL marker");
            reconstructed.append(sql.substring(start + MARKER.length()));
            String description = file.filename().substring(prefix.length(), file.filename().length() - 4).replace('_', ' ');
            migrations.add(new Migration("20261110.02.%03d".formatted(i + 1), file.filename(), description, checksum(sql)));
        }
        if (!MigrationProject.sha256(reconstructed.toString().getBytes(StandardCharsets.UTF_8)).equals(ORIGINAL_SHA256))
            throw new IllegalArgumentException("Split seed SQL differs from original SQL or order");
        return new Sources(manifest, checksum(reconstructed.toString()), List.copyOf(migrations), reconstructed.toString());
    }

    private static List<Entry> history(Connection connection) throws Exception {
        List<Entry> entries = new ArrayList<>();
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("""
                SELECT installed_rank, version, description, type, script, checksum,
                       installed_by, installed_on, execution_time, success
                FROM flyway_schema_history ORDER BY installed_rank
                """)) {
            while (rows.next()) entries.add(new Entry(rows.getInt(1), rows.getString(2), rows.getString(3),
                    rows.getString(4), rows.getString(5), (Integer) rows.getObject(6), rows.getString(7),
                    rows.getTimestamp(8), rows.getInt(9), rows.getBoolean(10)));
        }
        return entries;
    }

    /** apply=false 只读预检；apply=true 需单独指定全新的库外备份路径。 */
    public static String run(Connection connection, Path root, Path contractFile, Path backup, boolean apply) throws Exception {
        var source = sources(root);
        var contract = SchemaContract.read(contractFile, root);
        if (!contract.schemaVersion().equals("20261110.02.011"))
            throw new IllegalArgumentException("Seed split requires the current 20261110.02.011 schema contract");
        if (!connection.getAutoCommit()) throw new IllegalArgumentException("Requires an independent connection");
        SchemaSnapshot.version(connection);
        int isolation = connection.getTransactionIsolation();
        connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
        connection.setAutoCommit(false);
        try {
            if (apply) {
                try (var statement = connection.createStatement()) {
                    statement.execute("LOCK TABLE flyway_schema_history IN ACCESS EXCLUSIVE MODE");
                }
            } else connection.setReadOnly(true);
            contract.check(connection);
            var rows = history(connection);
            if (rows.isEmpty()) throw new IllegalArgumentException("Missing migration history");
            var schema = rows.getFirst();
            int schemaChecksum = checksum(Files.readString(root.resolve("sql/migrations/V20261110_01__postgresql_schema.sql")));
            boolean knownSchema = schema.success() && schema.version().equals("20261110.01")
                    && ((schema.type().equals("SQL") && Integer.valueOf(schemaChecksum).equals(schema.checksum())
                        && schema.script().equals("V20261110_01__postgresql_schema.sql"))
                        || (schema.type().equals("BASELINE") && schema.checksum() == null));
            if (!knownSchema) throw new IllegalArgumentException("Unknown structure migration history");
            if (rows.size() == 12) {
                for (int i = 0; i < source.migrations().size(); i++) {
                    var row = rows.get(i + 1); var migration = source.migrations().get(i);
                    if (!row.success() || !row.type().equals("SQL") || !row.version().equals(migration.version())
                            || !row.script().equals(migration.filename())
                            || !Integer.valueOf(migration.checksum()).equals(row.checksum()))
                        throw new IllegalArgumentException("Split migration history differs");
                }
                connection.rollback();
                return "Seed split is already registered; no changes";
            }
            if (rows.size() != 2) throw new IllegalArgumentException("Requires exactly schema .01 and legacy seed .02; unknown/later history is refused");
            var old = rows.getLast();
            if (!old.success() || !old.type().equals("SQL") || !old.version().equals("20261110.02")
                    || !old.script().equals(source.manifest().original())
                    || !Integer.valueOf(source.originalChecksum()).equals(old.checksum()) || old.rank() <= schema.rank())
                throw new IllegalArgumentException("Legacy seed history checksum or metadata differs");
            if (!apply) {
                connection.rollback();
                return "Seed split preflight passed; business data and migration history unchanged";
            }
            if (backup == null || backup.toAbsolutePath().normalize().startsWith(root.toAbsolutePath().normalize()))
                throw new IllegalArgumentException("seed.split.backup must be a new file outside the repository");
            Files.createFile(backup, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            SchemaContract.JSON.writeValue(backup.toFile(), Map.of("originalSha256", ORIGINAL_SHA256, "history", rows));
            try (var delete = connection.prepareStatement("DELETE FROM flyway_schema_history WHERE installed_rank = ?")) {
                delete.setInt(1, old.rank());
                if (delete.executeUpdate() != 1) throw new IllegalStateException("Legacy history row changed concurrently");
            }
            try (var insert = connection.prepareStatement("""
                    INSERT INTO flyway_schema_history
                        (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success)
                    VALUES (?, ?, ?, 'SQL', ?, ?, ?, ?, ?, TRUE)
                    """)) {
                for (int i = 0; i < source.migrations().size(); i++) {
                    var migration = source.migrations().get(i);
                    insert.setInt(1, old.rank() + i); insert.setString(2, migration.version());
                    insert.setString(3, migration.description()); insert.setString(4, migration.filename());
                    insert.setInt(5, migration.checksum()); insert.setString(6, old.installedBy());
                    insert.setTimestamp(7, old.installedOn()); insert.setInt(8, i == 0 ? old.executionTime() : 0);
                    insert.executeUpdate();
                }
            }
            connection.commit();
            return "Registered 11 split seed migrations; original history backed up; business data unchanged";
        } catch (Exception error) {
            connection.rollback();
            throw error;
        } finally {
            connection.setAutoCommit(true);
            connection.setReadOnly(false);
            connection.setTransactionIsolation(isolation);
        }
    }
}
