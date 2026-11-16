package top.aiolife.migration;

import java.sql.DriverManager;
import java.time.Duration;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import static org.junit.jupiter.api.Assertions.*;

/** 真实 PostgreSQL 验证新增对外 UUID，内部主键、导入引用和所有原字段均保留。 */
class AttachmentFileKeyMigrationIT {
    @Test void 回填附件UUIDv4且保留全部内部标识及引用() throws Exception {
        try (var pg = new GenericContainer<>("postgres:18-alpine")
                .withEnv("POSTGRES_PASSWORD", "fixture").withEnv("POSTGRES_DB", "fixture")
                .withExposedPorts(5432)
                .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\n", 2))
                .withStartupTimeout(Duration.ofMinutes(2))) {
            pg.start();
            String url = "jdbc:postgresql://" + pg.getHost() + ":" + pg.getMappedPort(5432) + "/fixture";
            var config = Flyway.configure().dataSource(url, "postgres", "fixture")
                    .locations("filesystem:" + MigrationProject.root().resolve("sql/migrations"))
                    .validateMigrationNaming(true).placeholderReplacement(false).callbacks(new MigrationGuard());
            config.target("20261116.11").load().migrate();
            try (var c = DriverManager.getConnection(url, "postgres", "fixture"); var q = c.createStatement()) {
                q.execute("INSERT INTO storage_object(id,bucket,object_key,content_hash,file_size,content_type) VALUES(11,'fixture','objects/aa/" + "a".repeat(64) + ".png','" + "a".repeat(64) + "',68,'image/png')");
                q.execute("INSERT INTO external_resource(id,platform,resource_type,external_key,source_url,state,storage_object_id) VALUES(21,'bilibili','video_cover','fixture','https://example.test','READY',11)");
                q.execute("INSERT INTO attachment(id,user_id,storage_object_id,biz_type,biz_id,is_deleted) VALUES(101,42,11,'avatar',42,0),(102,42,11,'b_video_cover',52,1)");
                q.execute("INSERT INTO import_job(id,biz_type,biz_id,external_resource_id,state,result_attachment_id) VALUES(201,'b_video_cover',51,21,'READY',101),(202,'b_video_cover',52,21,'READY',102),(203,'b_video_cover',53,21,'PENDING',NULL)");
                var attachments = SchemaSnapshot.rows(c, "SELECT to_jsonb(a) FROM attachment a ORDER BY id");
                var jobs = SchemaSnapshot.rows(c, "SELECT to_jsonb(j) FROM import_job j ORDER BY id");
                var objects = SchemaSnapshot.rows(c, "SELECT to_jsonb(o) FROM storage_object o ORDER BY id");
                var sources = SchemaSnapshot.rows(c, "SELECT to_jsonb(r) FROM external_resource r ORDER BY id");
                config.target("latest").load().migrate();
                config.target("latest").load().validate();
                var keys = SchemaSnapshot.rows(c, "SELECT file_key FROM attachment ORDER BY id");
                assertEquals(0, config.target("latest").load().migrate().migrationsExecuted);
                assertEquals(keys, SchemaSnapshot.rows(c, "SELECT file_key FROM attachment ORDER BY id"));
                assertEquals(attachments, SchemaSnapshot.rows(c, "SELECT to_jsonb(a)-'file_key' FROM attachment a ORDER BY id"));
                assertEquals(jobs, SchemaSnapshot.rows(c, "SELECT to_jsonb(j) FROM import_job j ORDER BY id"));
                assertEquals(objects, SchemaSnapshot.rows(c, "SELECT to_jsonb(o) FROM storage_object o ORDER BY id"));
                assertEquals(sources, SchemaSnapshot.rows(c, "SELECT to_jsonb(r) FROM external_resource r ORDER BY id"));
                assertEquals("2", SchemaSnapshot.rows(c, "SELECT count(*) FROM attachment WHERE uuid_extract_version(file_key)=4").getFirst().getFirst());
                assertEquals("2", SchemaSnapshot.rows(c, "SELECT count(DISTINCT file_key) FROM attachment").getFirst().getFirst());
                assertEquals("2", SchemaSnapshot.rows(c, "SELECT count(*) FROM information_schema.columns WHERE table_schema=current_schema() AND data_type='bigint' AND ((table_name='attachment' AND column_name='id') OR (table_name='import_job' AND column_name='result_attachment_id'))").getFirst().getFirst());
                assertThrows(Exception.class, () -> q.execute("UPDATE attachment SET file_key=NULL WHERE id=101"));
                assertThrows(Exception.class, () -> q.execute("UPDATE attachment SET file_key='1234567890123456789' WHERE id=101"));
                assertThrows(Exception.class, () -> q.execute("UPDATE attachment SET file_key='019535d9-3df7-79fb-b466-fa907fa17f9e' WHERE id=101"));
                assertThrows(Exception.class, () -> q.execute("UPDATE attachment SET file_key=(SELECT file_key FROM attachment WHERE id=101) WHERE id=102"));
            }
        }
    }
}
