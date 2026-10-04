package top.aiolife.record.api;

import top.aiolife.system.api.MenuController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MenuController 集成测试
 * 用于验证 SQL 脚本执行是否正确
 *
 * @author Lys
 * @date 2026/05/23
 */
class MenuControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MenuController menuController;

    @Test
    void testAll_获取菜单路由() {
        var response = menuController.all();
        assertSuccess(response);
        assertNotNull(response.getData());
    }
}
