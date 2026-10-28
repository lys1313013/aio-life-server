package top.aiolife.record.api;

import cn.dev33.satoken.stp.StpUtil;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import top.aiolife.core.constant.ResponseCodeConst;
import top.aiolife.record.mapper.IBVideoMapper;
import top.aiolife.record.service.VideoCoverService;
import top.aiolife.sso.service.SecondaryLockPolicy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 真实 Mapper SQL 与 HTTP 参数绑定，验证插件精确查询的隔离、大小写和重复记录边界。 */
class BVideoLookupTest {
    private static final String BVID = "BV1AbCdEfGh2";
    private static final long OWN_ID = 9007199254740993L;
    private HomeDetailTestSupport database;
    private MockMvc mvc;

    @BeforeEach
    void setUp() throws Exception {
        database = new HomeDetailTestSupport();
        // 模拟生产 utf8mb4_unicode_ci 的不区分大小写比较，确保二进制条件实际生效。
        database.jdbc.execute("ALTER TABLE b_video ALTER COLUMN bvid VARCHAR_IGNORECASE(20)");
        mvc = database.mvc(new BVideoController(database.session.getMapper(IBVideoMapper.class), mock(VideoCoverService.class)));
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    private void insert(long id, long userId, String bvid, String state, int deleted, String updated) {
        database.jdbc.update("""
                INSERT INTO b_video(id,user_id,bvid,title,url,duration,watched_duration,episodes,
                    current_episode,status,is_deleted,update_time)
                VALUES (?,?,?,'测试视频','https://www.bilibili.com/video/test',600,120,5,2,?,?,?)
                """, id, userId, bvid, state, deleted, updated);
    }

    @Test
    void testLookup_按本人和BV号读取并保持字符串ID及数值进度() throws Exception {
        insert(OWN_ID, 7, BVID, "in_progress", 0, "2026-10-28 10:00:00");
        insert(10, 8, BVID, "not_started", 0, "2026-10-28 11:00:00");
        insert(11, 7, BVID, "not_started", 1, "2026-10-28 11:00:00");
        try (var identity = mockStatic(StpUtil.class)) {
            identity.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            mvc.perform(get("/b-video/by-bvid").param("bvid", BVID).param("userId", "8"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.id").value(Long.toString(OWN_ID)))
                    .andExpect(jsonPath("$.data.bvid").value(BVID))
                    .andExpect(jsonPath("$.data.status").value("in_progress"))
                    .andExpect(jsonPath("$.data.watchedDuration").value(120))
                    .andExpect(jsonPath("$.data.currentEpisode").value(2))
                    .andExpect(jsonPath("$.data.userId").doesNotExist());
        }
    }

    @Test
    void testLookup_其他用户软删除及大小写不同均视为未标记() throws Exception {
        insert(10, 8, BVID, "not_started", 0, "2026-10-28 10:00:00");
        insert(11, 7, BVID, "not_started", 1, "2026-10-28 10:00:00");
        insert(12, 7, "BV1aBcDeFgH2", "not_started", 0, "2026-10-28 10:00:00");
        try (var identity = mockStatic(StpUtil.class)) {
            identity.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            for (String bvid : List.of(BVID, "BV9AbCdEfGh2")) {
                mvc.perform(get("/b-video/by-bvid").param("bvid", bvid))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.code").value(0))
                        .andExpect(jsonPath("$.data").isEmpty());
            }
        }
    }

    @Test
    void testLookup_重复标记按原列表状态和更新时间排序且同时间结果稳定() throws Exception {
        insert(20, 7, BVID, "completed", 0, "2026-10-28 12:00:00");
        insert(21, 7, BVID, "not_started", 0, "2026-10-28 09:00:00");
        insert(22, 7, BVID, "not_started", 0, "2026-10-28 10:00:00");
        insert(23, 7, BVID, "not_started", 0, "2026-10-28 10:00:00");
        try (var identity = mockStatic(StpUtil.class)) {
            identity.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            mvc.perform(get("/b-video/by-bvid").param("bvid", BVID))
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.id").value("23"));
        }
    }

    @Test
    void testLookup_缺失空值及非法BV号拒绝() throws Exception {
        mvc.perform(get("/b-video/by-bvid"))
                .andExpect(jsonPath("$.code").value(ResponseCodeConst.PARAM_FAIL));
        for (String bvid : List.of("", "BVtarget", "bv1AbCdEfGh2", BVID + "1", BVID + "' OR 1=1")) {
            mvc.perform(get("/b-video/by-bvid").param("bvid", bvid))
                    .andExpect(jsonPath("$.code").value(ResponseCodeConst.PARAM_FAIL));
        }
    }

    @Test
    void testLookup_沿用视频业务二级锁路由() {
        assertEquals(Set.of("/record/videoWatch"), SecondaryLockPolicy.requestMenus("/b-video/by-bvid"));
    }
}
