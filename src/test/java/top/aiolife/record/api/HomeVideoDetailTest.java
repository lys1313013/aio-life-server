package top.aiolife.record.api;

import cn.dev33.satoken.stp.StpUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import top.aiolife.core.constant.ResponseCodeConst;
import top.aiolife.record.mapper.IBVideoMapper;
import top.aiolife.record.service.VideoCoverService;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HomeVideoDetailTest {
    private static final long OWN_ID = 9007199254740993L;
    private static final String FULL_NOTES = "完整学习笔记\n" + "编辑时保留全部内容。".repeat(200);
    private HomeDetailTestSupport database;
    private MockMvc mvc;

    @BeforeEach
    void setUp() throws Exception {
        database = new HomeDetailTestSupport();
        mvc = database.mvc(new BVideoController(database.session.getMapper(IBVideoMapper.class), mock(VideoCoverService.class)));
        insert(OWN_ID, 7, 0);
        insert(20, 8, 0);
        insert(30, 7, 1);
    }

    private void insert(long id, long user, int deleted) {
        database.jdbc.update("INSERT INTO b_video(id,user_id,title,url,duration,watched_duration,episodes,current_episode,status,notes,bvid,description,is_deleted) VALUES (?,?,'完整视频','https://www.bilibili.com/video/BV123',600,120,5,2,'in_progress',?,'BV123','保留原始视频简介',?)", id, user, FULL_NOTES, deleted);
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    @Test
    void testDetail_真实SQL返回本人编辑字段及字符串ID和数值进度() throws Exception {
        try (var identity = mockStatic(StpUtil.class)) {
            identity.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            mvc.perform(get("/b-video/{id}", OWN_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.id").value(Long.toString(OWN_ID)))
                    .andExpect(jsonPath("$.data.title").value("完整视频"))
                    .andExpect(jsonPath("$.data.notes").value(FULL_NOTES))
                    .andExpect(jsonPath("$.data.description").value("保留原始视频简介"))
                    .andExpect(jsonPath("$.data.status").value("in_progress"))
                    .andExpect(jsonPath("$.data.duration").value(600))
                    .andExpect(jsonPath("$.data.watchedDuration").value(120))
                    .andExpect(jsonPath("$.data.currentEpisode").value(2))
                    .andExpect(jsonPath("$.data.episodes").value(5))
                    .andExpect(jsonPath("$.data.userId").doesNotExist())
                    .andExpect(jsonPath("$.data.createUser").doesNotExist());
        }
    }

    @Test
    void testDetail_他人ID和伪造用户参数不可获取完整视频() throws Exception {
        try (var identity = mockStatic(StpUtil.class)) {
            identity.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            mvc.perform(get("/b-video/20").param("userId", "8"))
                    .andExpect(jsonPath("$.code").value(ResponseCodeConst.COMMON_FAIL))
                    .andExpect(jsonPath("$.message").value("视频不存在或无权访问"))
                    .andExpect(jsonPath("$.data").doesNotExist());
        }
    }

    @Test
    void testDetail_软删除与不存在视频统一拒绝() throws Exception {
        try (var identity = mockStatic(StpUtil.class)) {
            identity.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            for (long id : new long[]{30, 99}) {
                mvc.perform(get("/b-video/{id}", id))
                        .andExpect(jsonPath("$.code").value(ResponseCodeConst.COMMON_FAIL))
                        .andExpect(jsonPath("$.message").value("视频不存在或无权访问"))
                        .andExpect(jsonPath("$.data").doesNotExist());
            }
        }
    }
}
