package top.aiolife.database;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import top.aiolife.record.mapper.ExternalResourceMapper;
import top.aiolife.record.pojo.entity.ExternalResourceEntity;
import top.aiolife.support.AttachmentDatabaseFixture;
import static org.junit.jupiter.api.Assertions.*;

class PostgresqlTaskLeaseIntegrationTest extends AttachmentDatabaseFixture {
    @Test void 来源租约排他且过期后旧令牌不能写回() {
        var sources=mapper(ExternalResourceMapper.class);var entry=new ExternalResourceEntity();
        entry.fillCreateCommonField(42L);entry.setPlatform("bilibili");entry.setResourceType("video_cover");
        entry.setExternalKey("bfs/archive/test");entry.setVariant("");entry.setSourceUrl("https://i0.hdslb.com/bfs/archive/test.jpg");
        entry.setState("PENDING");entry.setAttempts(0);sources.insert(entry);
        assertEquals(1,sources.claim(entry.getId(),"first"));assertEquals(0,sources.claim(entry.getId(),"second"));
        var claimed=sources.selectById(entry.getId());assertEquals(1,claimed.getAttempts());
        assertTrue(claimed.getLeaseUntil().isAfter(LocalDateTime.now().plusSeconds(100)));
        jdbc.update("UPDATE external_resource SET lease_until=CURRENT_TIMESTAMP-INTERVAL '1 second',attempts=5");
        assertEquals(1,sources.claim(entry.getId(),"second"));
        transaction.executeWithoutResult(t->{assertNull(sources.lockClaim(entry.getId(),"first"));assertNotNull(sources.lockClaim(entry.getId(),"second"));});
    }
}
