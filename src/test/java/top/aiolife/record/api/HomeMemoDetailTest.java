package top.aiolife.record.api;

import cn.dev33.satoken.stp.StpUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import top.aiolife.core.constant.ResponseCodeConst;
import top.aiolife.record.mapper.IMemoMapper;
import top.aiolife.record.service.IMemoService;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HomeMemoDetailTest {
    private static final long OWN_ID = 9007199254740993L;
    private static final String FULL_CONTENT = "# 完整正文\n\n隐藏内容仍须在显式编辑时获取。\n" + "长内容不能截断，".repeat(200);
    private HomeDetailTestSupport database;
    private MockMvc mvc;

    @BeforeEach
    void setUp() throws Exception {
        database = new HomeDetailTestSupport();
        mvc = database.mvc(new MemoController(mock(IMemoService.class), database.session.getMapper(IMemoMapper.class)));
        database.jdbc.update("INSERT INTO memo(id,user_id,title,content,hidden_content,is_deleted) VALUES (?,7,'隐藏笔记',?,1,0)", OWN_ID, FULL_CONTENT);
        database.jdbc.update("INSERT INTO memo(id,user_id,title,content,hidden_content,is_deleted) VALUES (20,8,'他人笔记','他人正文',0,0),(30,7,'已删除笔记','已删除正文',0,1)");
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    @Test
    void testDetail_真实SQL读取本人隐藏笔记完整正文并保持字符串ID() throws Exception {
        try (var identity = mockStatic(StpUtil.class)) {
            identity.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            mvc.perform(get("/memo/{id}", OWN_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.id").value(Long.toString(OWN_ID)))
                    .andExpect(jsonPath("$.data.title").value("隐藏笔记"))
                    .andExpect(jsonPath("$.data.content").value(FULL_CONTENT))
                    .andExpect(jsonPath("$.data.hiddenContent").value(true))
                    .andExpect(jsonPath("$.data.userId").doesNotExist())
                    .andExpect(jsonPath("$.data.createUser").doesNotExist());
        }
    }

    @Test
    void testDetail_他人ID及伪造查询用户不能读取正文() throws Exception {
        try (var identity = mockStatic(StpUtil.class)) {
            identity.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            mvc.perform(get("/memo/20").param("userId", "8"))
                    .andExpect(jsonPath("$.code").value(ResponseCodeConst.COMMON_FAIL))
                    .andExpect(jsonPath("$.message").value("笔记不存在或无权访问"))
                    .andExpect(jsonPath("$.data").doesNotExist());
        }
    }

    @Test
    void testDetail_软删除与不存在ID统一拒绝() throws Exception {
        try (var identity = mockStatic(StpUtil.class)) {
            identity.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            for (long id : new long[]{30, 99}) {
                mvc.perform(get("/memo/{id}", id))
                        .andExpect(jsonPath("$.code").value(ResponseCodeConst.COMMON_FAIL))
                        .andExpect(jsonPath("$.message").value("笔记不存在或无权访问"))
                        .andExpect(jsonPath("$.data").doesNotExist());
            }
        }
    }
}
