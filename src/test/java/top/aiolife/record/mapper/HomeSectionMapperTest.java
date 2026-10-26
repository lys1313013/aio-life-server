package top.aiolife.record.mapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import top.aiolife.core.pojo.entity.BaseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 隔离 H2 真执行生产 Mapper SQL；不访问用户数据库。 */
class HomeSectionMapperTest {
    private JdbcTemplate jdbc;
    private SqlSession session;
    private HomeSectionMapper mapper;

    @BeforeEach
    void setUp() {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:home_sections_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE memo (id BIGINT, user_id BIGINT, title VARCHAR(255), content TEXT, hidden_content BOOLEAN, update_time TIMESTAMP, is_deleted INT DEFAULT 0)");
        jdbc.execute("CREATE TABLE b_video (id BIGINT, user_id BIGINT, title VARCHAR(255), url VARCHAR(512), bvid VARCHAR(64), current_episode INT, episodes INT, watched_duration INT, duration INT, last_watched TIMESTAMP, cover_file_id VARCHAR(32), cover_state VARCHAR(16), update_time TIMESTAMP, create_time TIMESTAMP, status VARCHAR(32), is_deleted INT DEFAULT 0)");
        jdbc.execute("CREATE TABLE device (id BIGINT, user_id BIGINT, name VARCHAR(255), purchase_date DATE, purchase_price DECIMAL(12,2), end_date DATE, file_id VARCHAR(32), status VARCHAR(16), is_deleted INT DEFAULT 0)");
        jdbc.execute("CREATE TABLE income (user_id BIGINT, amt DECIMAL(12,2), inc_date DATE, is_deleted INT DEFAULT 0)");
        jdbc.execute("CREATE TABLE expense (user_id BIGINT, amt DECIMAL(12,2), exp_time TIMESTAMP, is_deleted INT DEFAULT 0)");
        var configuration = new Configuration(new Environment("test", new JdbcTransactionFactory(), source));
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(HomeSectionMapper.class);
        session = new SqlSessionFactoryBuilder().build(configuration).openSession();
        mapper = session.getMapper(HomeSectionMapper.class);
    }

    @AfterEach
    void tearDown() {
        if (session != null) session.close();
        if (jdbc != null) jdbc.execute("DROP ALL OBJECTS");
    }

    @Test
    void testMemos_最近三条稳定排序隔离用户和删除且不查询正文() {
        for (long id = 1; id <= 4; id++) {
            jdbc.update("INSERT INTO memo VALUES (?,1,?,'secret body',true,'2026-10-22 12:00:00',0)", id, "memo-" + id);
        }
        jdbc.update("INSERT INTO memo VALUES (5,2,'other','private',false,'2026-10-23 12:00:00',0)");
        jdbc.update("INSERT INTO memo VALUES (6,1,'deleted','private',false,'2026-10-23 12:00:00',1)");
        var rows = mapper.recentMemos(1);
        assertEquals(List.of(4L, 3L, 2L), rows.stream().map(BaseEntity::getId).toList());
        rows.forEach(row -> {
            assertNull(row.getContent());
            assertTrue(row.getHiddenContent());
        });
        // 正文列不存在时仍可执行，避免未来回归为 SELECT *。
        jdbc.execute("ALTER TABLE memo DROP COLUMN content");
        session.clearCache();
        assertEquals(3, mapper.recentMemos(1).size());
        assertTrue(mapper.recentMemos(99).isEmpty());
    }

    @Test
    void testVideos_只返回续看三条且按观看更新创建时间回退排序() {
        video(1, 1, "in_progress", 0, null, null, "2026-10-01 10:00:00");
        video(2, 1, "in_progress", 0, null, "2026-10-22 10:00:00", "2026-10-01 10:00:00");
        video(3, 1, "in_progress", 0, "2026-10-22 10:00:00", "2026-10-23 10:00:00", "2026-10-01 10:00:00");
        video(4, 1, "in_progress", 0, "2026-10-24 10:00:00", null, null);
        video(5, 2, "in_progress", 0, "2026-10-25 10:00:00", null, null);
        video(6, 1, "in_progress", 1, "2026-10-25 10:00:00", null, null);
        video(7, 1, "completed", 0, "2026-10-25 10:00:00", null, null);
        video(8, 1, "not_started", 0, "2026-10-25 10:00:00", null, null);
        assertEquals(List.of(4L, 3L, 2L), mapper.continuingVideos(1).stream().map(BaseEntity::getId).toList());
        assertTrue(mapper.continuingVideos(99).isEmpty());
    }

    private void video(long id, long user, String status, int deleted, String watched, String updated, String created) {
        jdbc.update("INSERT INTO b_video (id,user_id,title,status,is_deleted,last_watched,update_time,create_time) VALUES (?,?,?,?,?,?,?,?)", id, user, "video-" + id, status, deleted, watched, updated, created);
    }

    @Test
    void testDevices_仅在用三条按购买日期和编号排序并隔离归属() {
        for (int id = 1; id <= 4; id++) {
            jdbc.update("INSERT INTO device (id,user_id,name,purchase_date,purchase_price,end_date,status,is_deleted) VALUES (?,1,?,'2026-10-22',100,NULL,'1',0)", id, "device-" + id);
        }
        jdbc.update("INSERT INTO device (id,user_id,name,purchase_date,purchase_price,end_date,status,is_deleted) VALUES (5,2,'other','2026-10-23',100,NULL,'1',0)");
        jdbc.update("INSERT INTO device (id,user_id,name,purchase_date,purchase_price,end_date,status,is_deleted) VALUES (6,1,'deleted','2026-10-23',100,NULL,'1',1)");
        jdbc.update("INSERT INTO device (id,user_id,name,purchase_date,purchase_price,end_date,status,is_deleted) VALUES (7,1,'retired','2026-10-23',100,NULL,'2',0)");
        assertEquals(List.of(4L, 3L, 2L), mapper.activeDevices(1).stream().map(BaseEntity::getId).toList());
        assertTrue(mapper.activeDevices(99).isEmpty());
    }





    @Test
    void testImages_查询返回存储文件ID与封面状态而非原图地址() {
        jdbc.update("INSERT INTO b_video(id,user_id,title,status,last_watched,cover_file_id,cover_state) VALUES (1,7,'video','in_progress','2026-10-22 12:00:00','9007199254740993','READY')");
        jdbc.update("INSERT INTO device(id,user_id,name,status,file_id) VALUES (1,7,'device','1','9007199254740995')");
        var video = mapper.continuingVideos(7).getFirst();
        assertEquals("9007199254740993", video.getCoverFileId());
        assertEquals("READY", video.getCoverState());
        assertNull(video.getCover());
        assertEquals("9007199254740995", mapper.activeDevices(7).getFirst().getFileId());
    }

}
