package top.aiolife.database;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import top.aiolife.record.mapper.ImageImportTaskMapper;
import top.aiolife.record.pojo.entity.ImageImportTaskEntity;
import static org.junit.jupiter.api.Assertions.*;

/** 用真实 PostgreSQL 验证秒精度排队、时间区间 SQL 与锁领取，避免 H2 掩盖方言差异。 */
@ActiveProfiles("test")
@SpringBootTest(classes=PhysicalTableEntitySelectIntegrationTest.DatabaseMapperTestApplication.class,
        webEnvironment=SpringBootTest.WebEnvironment.NONE)
@Transactional
class PostgresqlTaskLeaseIntegrationTest {
    @Autowired ImageImportTaskMapper tasks;

    @Test void 新任务可立即领取且租约写入未来时间() {
        var entry=new ImageImportTaskEntity();
        entry.fillCreateCommonField(880000000000000701L);
        entry.setVideoId(880000000000000702L); entry.setCoverVersion(1L);
        entry.setSourceUrl("https://example.test/fixture.png");
        entry.setState("PENDING"); entry.setAttempts(0);
        entry.setNextAttemptAt(LocalDateTime.now().withNano(0));
        tasks.insert(entry);
        assertTrue(tasks.due().stream().anyMatch(t -> t.getId().equals(entry.getId())));
        assertEquals(1,tasks.claim(entry.getId(),"fixture-lease"));
        var claimed=tasks.lockClaim(entry.getId(),"fixture-lease");
        assertEquals(1,claimed.getAttempts());
        assertTrue(claimed.getLeaseUntil().isAfter(LocalDateTime.now().plusSeconds(100)));
        assertEquals(0,tasks.claim(entry.getId(),"other-lease"));
    }
}
