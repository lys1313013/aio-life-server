package top.aiolife.record.mcp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import top.aiolife.record.api.BaseIntegrationTest;
import top.aiolife.record.mapper.IAnniversaryRecordMapper;
import top.aiolife.record.mcp.req.AnniversaryQueryMcpReq;
import top.aiolife.record.mcp.vo.AnniversaryMcpVO;
import top.aiolife.record.pojo.entity.AnniversaryRecordEntity;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AnniversaryMcpTools 集成测试
 *
 * @author Lys
 * @date 2026/08/30
 */
class AnniversaryMcpToolsIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private AnniversaryMcpTools anniversaryMcpTools;

    @Autowired
    private IAnniversaryRecordMapper anniversaryRecordMapper;

    @Test
    void testAnniversaryQuery_类型返回中文标签_按距今天数升序() {
        LocalDate today = LocalDate.now();
        Long id1 = System.currentTimeMillis() % 1000000 + 900000L;
        Long id2 = id1 + 1;
        anniversaryRecordMapper.insert(createAnniversary(id1, "MCP纪念日-远", "anniversary", today.plusDays(10)));
        anniversaryRecordMapper.insert(createAnniversary(id2, "MCP倒数日-近", "countdown", today.plusDays(3)));

        AnniversaryQueryMcpReq req = new AnniversaryQueryMcpReq();
        List<AnniversaryMcpVO> result = anniversaryMcpTools.anniversary_query(req);

        AnniversaryMcpVO near = findByTitle(result, "MCP倒数日-近");
        AnniversaryMcpVO far = findByTitle(result, "MCP纪念日-远");
        assertEquals("倒数日", near.getType());
        assertEquals("纪念日", far.getType());
        assertEquals(3, near.getDaysRemaining());
        assertEquals(10, far.getDaysRemaining());
        assertTrue(result.indexOf(near) < result.indexOf(far), "应按距今天数升序排列");
    }

    @Test
    void testAnniversaryQuery_纪念日跨年计算下一次发生() {
        LocalDate today = LocalDate.now();
        // 构造一个今年已过的纪念日（昨天），应算到明年
        Long id = System.currentTimeMillis() % 1000000 + 900000L;
        anniversaryRecordMapper.insert(createAnniversary(id, "MCP纪念日-跨年", "anniversary", today.minusDays(1)));

        AnniversaryQueryMcpReq req = new AnniversaryQueryMcpReq();
        List<AnniversaryMcpVO> result = anniversaryMcpTools.anniversary_query(req);

        AnniversaryMcpVO vo = findByTitle(result, "MCP纪念日-跨年");
        LocalDate next = today.minusDays(1).plusYears(1);
        assertEquals(ChronoUnit.DAYS.between(today, next), vo.getDaysRemaining());
    }

    @Test
    void testAnniversaryQuery_withinDays过滤() {
        LocalDate today = LocalDate.now();
        Long id1 = System.currentTimeMillis() % 1000000 + 900000L;
        Long id2 = id1 + 1;
        anniversaryRecordMapper.insert(createAnniversary(id1, "MCP临近-7天内", "countdown", today.plusDays(5)));
        anniversaryRecordMapper.insert(createAnniversary(id2, "MCP临近-7天外", "countdown", today.plusDays(30)));

        AnniversaryQueryMcpReq req = new AnniversaryQueryMcpReq();
        req.setWithinDays(7);
        List<AnniversaryMcpVO> result = anniversaryMcpTools.anniversary_query(req);

        assertNotNull(findByTitle(result, "MCP临近-7天内"));
        assertThrows(AssertionError.class, () -> findByTitle(result, "MCP临近-7天外"));
    }

    @Test
    void testAnniversaryQuery_类型筛选与非法类型() {
        LocalDate today = LocalDate.now();
        Long id = System.currentTimeMillis() % 1000000 + 900000L;
        anniversaryRecordMapper.insert(createAnniversary(id, "MCP筛选-倒数日", "countdown", today.plusDays(5)));

        AnniversaryQueryMcpReq req = new AnniversaryQueryMcpReq();
        req.setType("倒数日");
        assertNotNull(findByTitle(anniversaryMcpTools.anniversary_query(req), "MCP筛选-倒数日"));

        req.setType("纪念日");
        assertThrows(AssertionError.class,
                () -> findByTitle(anniversaryMcpTools.anniversary_query(req), "MCP筛选-倒数日"));

        req.setType("生日");
        assertThrows(IllegalArgumentException.class, () -> anniversaryMcpTools.anniversary_query(req));
    }

    private AnniversaryMcpVO findByTitle(List<AnniversaryMcpVO> list, String title) {
        return list.stream()
                .filter(vo -> title.equals(vo.getTitle()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("未找到标题为「" + title + "」的记录"));
    }

    private AnniversaryRecordEntity createAnniversary(Long id, String title, String type, LocalDate targetDate) {
        AnniversaryRecordEntity entity = new AnniversaryRecordEntity();
        entity.setId(id);
        entity.setUserId(TEST_USER_ID);
        entity.setTitle(title);
        entity.setTargetDate(targetDate);
        entity.setType(type);
        entity.setIsDeleted(0);
        return entity;
    }
}
