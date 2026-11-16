package top.aiolife.migration;

import java.sql.DriverManager;
import java.time.Duration;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import static org.junit.jupiter.api.Assertions.*;

/** 真实PostgreSQL验证空库、已有附件准备门禁、回填和不可变来源约束。 */
class AttachmentMigrationIT {
    @Test void 空库初始化与已有附件增量保留() throws Exception {
        try(var pg=new GenericContainer<>("postgres:18-alpine").withEnv("POSTGRES_PASSWORD","fixture")
                .withEnv("POSTGRES_DB","fixture").withExposedPorts(5432)
                .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\n",2))
                .withStartupTimeout(Duration.ofMinutes(2))) {
            pg.start();
            String url="jdbc:postgresql://"+pg.getHost()+":"+pg.getMappedPort(5432)+"/fixture";
            var config=Flyway.configure().dataSource(url,"postgres","fixture")
                .locations("filesystem:"+MigrationProject.root().resolve("sql/migrations"))
                .validateMigrationNaming(true).placeholderReplacement(false).callbacks(new MigrationGuard());
            config.target("20261116.10").load().migrate();
            try(var c=DriverManager.getConnection(url,"postgres","fixture");var q=c.createStatement()) {
                q.execute("INSERT INTO file(id,file_name,biz_type,biz_id,create_user) VALUES('aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa','old/avatar.png','avatar',42,42)");
                assertThrows(Exception.class,()->config.target("latest").load().migrate());
                assertEquals("1",SchemaSnapshot.rows(c,"SELECT count(*) FROM file").getFirst().getFirst());
                q.execute("INSERT INTO storage_object(id,bucket,object_key,sha256,file_size,content_type,image_width,image_height) VALUES(11,'fixture','objects/aa/"+"a".repeat(64)+".png','"+"a".repeat(64)+"',68,'image/png',1,1)");
                q.execute("UPDATE file SET storage_object_id=11,upgrade_id=1234567890123456789");
            }
            config.target("latest").load().migrate();
            assertEquals(0,config.target("latest").load().migrate().migrationsExecuted);
            try(var c=DriverManager.getConnection(url,"postgres","fixture");var q=c.createStatement()) {
                assertEquals("42",SchemaSnapshot.rows(c,"SELECT user_id FROM attachment").getFirst().getFirst());
                assertEquals("1234567890123456789",SchemaSnapshot.rows(c,"SELECT id FROM attachment").getFirst().getFirst());
                assertEquals("4",SchemaSnapshot.rows(c,"SELECT uuid_extract_version(file_key) FROM attachment").getFirst().getFirst());
                assertEquals("1",SchemaSnapshot.rows(c,"SELECT is_public FROM attachment").getFirst().getFirst());
                assertNull(SchemaSnapshot.rows(c,"SELECT to_regclass('file')").getFirst().getFirst());
                assertNull(SchemaSnapshot.rows(c,"SELECT to_regclass('image_import_task')").getFirst().getFirst());
                assertEquals(69,SchemaSnapshot.read(c).sections().get("tables").size());
                assertTrue(SchemaSnapshot.read(c).sections().get("columns").stream().allMatch(x->x.get(9)!=null&&!x.get(9).isBlank()));
                assertThrows(Exception.class,()->q.execute("INSERT INTO external_resource(id,platform,resource_type,external_key,source_url,state) VALUES(1,'douban','book_cover','p1','https://example.test','READY')"));
            }
            try(var c=DriverManager.getConnection(url,"postgres","fixture");var q=c.createStatement()) { q.execute("CREATE SCHEMA empty"); }
            config.schemas("empty").defaultSchema("empty").target("latest").load().migrate();
        }
    }
}
