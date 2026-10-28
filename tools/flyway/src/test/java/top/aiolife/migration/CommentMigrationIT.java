package top.aiolife.migration;

import java.util.*;
import java.util.regex.Pattern;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static top.aiolife.migration.IncrementalMigrationIT.*;

class CommentMigrationIT {
    @ParameterizedTest @ValueSource(strings = {"mysql:5.7.44", "mysql:8.0.40", "mysql:8.4.2"})
    void preservesAttributesDataAndRejectsBeforeAlter(String image) throws Exception {
        try (var mysql = new IsolatedMysql(image)) {
            String db = mysql.database(); mysql.baselineFixture(db);
            String source = mysql.source(COMMENTS);
            var columns = tuples(source, "_aio_sc_columns", 4);
            var tables = tuples(source, "_aio_sc_tables", 2);
            assertEquals(73, columns.size()); assertEquals(7, tables.size());
            mysql.sql(db, "ALTER TABLE device MODIFY COLUMN spec VARCHAR(257) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL DEFAULT " + quote("quoted'path\\value") + " COMMENT 'old'; ALTER TABLE b_video MODIFY COLUMN last_watched DATETIME(6) NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'old'; ALTER TABLE user_dict_data MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'old'; ALTER TABLE record_article MODIFY COLUMN is_deleted TINYINT NULL DEFAULT 0 COMMENT 'old'");
            for (String table : columns.stream().map(List::getFirst).distinct().toList()) {
                String ddl = mysql.rows(db, "SHOW CREATE TABLE `" + table + "`").getFirst().get(1);
                List<String> alters = new ArrayList<>();
                for (var column : columns) if (column.getFirst().equals(table)) {
                    var line = Pattern.compile("(?m)^\\s*`" + column.get(1) + "` .+$").matcher(ddl);
                    assertTrue(line.find());
                    String definition = line.group().trim().replaceFirst(",$", "").replaceAll(" COMMENT '(?:\\\\.|''|[^'])*'", "");
                    alters.add("MODIFY COLUMN " + definition + " COMMENT 'old'");
                }
                mysql.sql(db, "ALTER TABLE `" + table + "` " + String.join(",", alters));
                insert(mysql, db, table, requiredValues(mysql, db, table));
            }
            for (var table : tables) mysql.sql(db, "ALTER TABLE `" + table.getFirst() + "` COMMENT='old'");
            var attributes = attributes(mysql, db); var data = mysql.data(db); var indexes = mysql.snapshot(db).sections().get("indexes");
            // Flyway 自身负责 DELIMITER；在同一执行脚本中确认会话模式恢复。
            mysql.script(db, "SET SESSION sql_mode='NO_BACKSLASH_ESCAPES';\n" + source + "\nCREATE TABLE mode_probe AS SELECT @@SESSION.sql_mode AS mode;");
            assertEquals("NO_BACKSLASH_ESCAPES", mysql.scalar(db, "SELECT mode FROM mode_probe"));
            mysql.sql(db, "DROP TABLE mode_probe");
            assertEquals(attributes, attributes(mysql, db)); assertEquals(data, mysql.data(db)); assertEquals(indexes, mysql.snapshot(db).sections().get("indexes"));
            for (var column : columns) assertEquals(column.get(3), mysql.scalar(db, "SELECT column_comment FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=" + quote(column.getFirst()) + " AND column_name=" + quote(column.get(1))));
            for (var table : tables) assertEquals(table.get(1), mysql.scalar(db, "SELECT table_comment FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=" + quote(table.getFirst())));
            var complete = mysql.snapshot(db); mysql.migration(db, COMMENTS); assertEquals(complete, mysql.snapshot(db)); assertEquals(data, mysql.data(db));
            mysql.sql(db, "ALTER TABLE b_video COMMENT='pending'; ALTER TABLE user_dict_type DROP COLUMN color"); failureWithoutAlter(mysql, db, COMMENTS);
            mysql.sql(db, "ALTER TABLE user_dict_type ADD COLUMN color INT"); failureWithoutAlter(mysql, db, COMMENTS);
            mysql.sql(db, "ALTER TABLE user_dict_type DROP COLUMN color,ADD COLUMN color VARCHAR(20)"); mysql.migration(db, COMMENTS);
            mysql.sql(db, "ALTER TABLE device DROP COLUMN spec,ADD COLUMN spec VARCHAR(257) AS (name); ALTER TABLE b_video COMMENT='pending'"); failureWithoutAlter(mysql, db, COMMENTS);
            mysql.sql(db, "ALTER TABLE device DROP COLUMN spec,ADD COLUMN spec VARCHAR(255); DROP TABLE message"); failureWithoutAlter(mysql, db, COMMENTS);
        }
    }
    private static List<List<String>> tuples(String source, String table, int width) {
        var block = Pattern.compile("INSERT INTO `" + table + "`.*?VALUES\\s*(.*?);", Pattern.DOTALL).matcher(source);
        assertTrue(block.find());
        String string = "'((?:''|[^'])*)'";
        var tuple = Pattern.compile("\\(" + String.join(", ", Collections.nCopies(width, string)) + "\\)").matcher(block.group(1));
        List<List<String>> result = new ArrayList<>();
        while (tuple.find()) {
            List<String> row = new ArrayList<>();
            for (int i = 1; i <= width; i++) row.add(tuple.group(i).replace("''", "'"));
            result.add(row);
        }
        return result;
    }
    private static List<List<List<String>>> attributes(IsolatedMysql mysql, String db) throws Exception {
        return List.of(mysql.rows(db, "SELECT table_name,column_name,column_type,is_nullable,column_default,extra,generation_expression,character_set_name,collation_name,ordinal_position FROM information_schema.columns WHERE table_schema=DATABASE() ORDER BY table_name,ordinal_position"),
                mysql.rows(db, "SELECT table_name,engine,table_collation,row_format,create_options FROM information_schema.tables WHERE table_schema=DATABASE() ORDER BY table_name"));
    }
}
