package top.aiolife.record.api;

import cn.dev33.satoken.stp.StpUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.MockedStatic;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import top.aiolife.core.constant.ResponseCodeConst;
import top.aiolife.core.resq.ApiResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;

/**
 * Controller 集成测试基类
 * 用于验证 SQL 脚本执行是否正确（如字段是否存在）
 *
 * @author Lys
 * @date 2026/05/23
 */
@SpringBootTest
@Transactional
public class BaseIntegrationTest {

    protected static final Long TEST_USER_ID = 1L;

    private MockedStatic<StpUtil> stpUtilMock;

    @BeforeEach
    void setUp() {
        stpUtilMock = mockStatic(StpUtil.class);
        stpUtilMock.when(StpUtil::getLoginIdAsLong).thenReturn(TEST_USER_ID);
    }

    @AfterEach
    void tearDown() {
        if (stpUtilMock != null) {
            stpUtilMock.close();
        }
    }

    /**
     * 断言 API 响应成功
     */
    protected void assertSuccess(ApiResponse<?> response) {
        assertNotNull(response, "响应对象不能为空");
        assertEquals(ResponseCodeConst.SUCCESS, response.getCode(), "响应状态码应为成功");
    }

    /**
     * 断言 API 响应成功并返回数据
     */
    protected <T> T assertSuccessWithData(ApiResponse<T> response) {
        assertSuccess(response);
        assertNotNull(response.getData(), "响应数据不能为空");
        return response.getData();
    }
}
