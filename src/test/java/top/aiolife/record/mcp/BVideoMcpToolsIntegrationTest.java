package top.aiolife.record.mcp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import top.aiolife.record.api.BaseIntegrationTest;
import top.aiolife.record.enums.StudyEnum;
import top.aiolife.record.mapper.IBVideoMapper;
import top.aiolife.record.mcp.req.BVideoQueryMcpReq;
import top.aiolife.record.mcp.vo.BVideoMcpVO;
import top.aiolife.record.mcp.vo.BVideoPageMcpVO;
import top.aiolife.record.mcp.vo.BVideoStatisticsMcpVO;
import top.aiolife.record.pojo.entity.BVideoEntity;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BVideoMcpTools 集成测试
 *
 * @author Lys
 * @date 2026/08/30
 */
class BVideoMcpToolsIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private BVideoMcpTools bVideoMcpTools;

    @Autowired
    private IBVideoMapper bVideoMapper;

    @Test
    void testBVideoQuery_查询学习视频_状态返回中文标签() {
        Long id = System.currentTimeMillis() % 1000000 + 900000L;
        bVideoMapper.insert(createVideo(id, "MCP测试视频-查询", StudyEnum.IN_PROGRESS));

        BVideoQueryMcpReq req = new BVideoQueryMcpReq();
        req.setTitle("MCP测试视频-查询");
        BVideoPageMcpVO result = bVideoMcpTools.b_video_query(req);

        assertNotNull(result);
        assertTrue(result.getTotal() >= 1);
        BVideoMcpVO vo = result.getRecords().stream()
                .filter(v -> id.equals(v.getId()))
                .findFirst()
                .orElseThrow();
        assertEquals("进行中", vo.getStatus());
        assertEquals(50.0, vo.getProgressPercentage());
    }

    @Test
    void testBVideoQuery_状态筛选() {
        Long id = System.currentTimeMillis() % 1000000 + 900000L;
        bVideoMapper.insert(createVideo(id, "MCP测试视频-已完成", StudyEnum.COMPLETED));

        BVideoQueryMcpReq req = new BVideoQueryMcpReq();
        req.setTitle("MCP测试视频-已完成");
        req.setStatus(StudyEnum.COMPLETED.getValue());
        BVideoPageMcpVO result = bVideoMcpTools.b_video_query(req);

        BVideoMcpVO vo = result.getRecords().stream()
                .filter(v -> id.equals(v.getId()))
                .findFirst()
                .orElseThrow();
        assertEquals("已完成", vo.getStatus());

        req.setStatus(StudyEnum.NOT_START.getValue());
        assertTrue(bVideoMcpTools.b_video_query(req).getRecords().stream()
                .noneMatch(v -> id.equals(v.getId())));
    }

    @Test
    void testBVideoQuery_未观看进度为0() {
        Long id = System.currentTimeMillis() % 1000000 + 900000L;
        BVideoEntity entity = createVideo(id, "MCP测试视频-未观看", StudyEnum.NOT_START);
        entity.setWatchedDuration(null);
        bVideoMapper.insert(entity);

        BVideoQueryMcpReq req = new BVideoQueryMcpReq();
        req.setTitle("MCP测试视频-未观看");
        BVideoMcpVO vo = bVideoMcpTools.b_video_query(req).getRecords().stream()
                .filter(v -> id.equals(v.getId()))
                .findFirst()
                .orElseThrow();
        assertEquals(0.0, vo.getProgressPercentage());
    }

    @Test
    void testBVideoStatistics_统计返回状态中文标签() {
        Long id = System.currentTimeMillis() % 1000000 + 900000L;
        bVideoMapper.insert(createVideo(id, "MCP测试视频-统计", StudyEnum.IN_PROGRESS));

        BVideoStatisticsMcpVO vo = bVideoMcpTools.b_video_statistics();

        assertNotNull(vo);
        assertTrue(vo.getTotalSeconds() >= 0);
        assertTrue(vo.getStudiedSeconds() >= 0);
        assertTrue(vo.getProgressPercentage() >= 0 && vo.getProgressPercentage() <= 100);
        if (vo.getStatusCounts() != null) {
            vo.getStatusCounts().keySet().forEach(key ->
                    assertTrue(key.matches("未开始|进行中|已暂停|部分完成|已完成|\\d+"),
                            "状态 key 应为中文标签: " + key));
        }
    }

    private BVideoEntity createVideo(Long id, String title, StudyEnum status) {
        BVideoEntity entity = new BVideoEntity();
        entity.setId(id);
        entity.setUserId(TEST_USER_ID);
        entity.setTitle(title);
        entity.setBvid("BV" + id);
        entity.setUrl("https://www.bilibili.com/video/BV" + id);
        entity.setDuration(600);
        entity.setWatchedDuration(300);
        entity.setStatus(status.getValue());
        entity.setLastWatched(LocalDateTime.now());
        entity.setIsDeleted(0);
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        return entity;
    }
}
