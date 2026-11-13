package top.aiolife.record.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import top.aiolife.record.mapper.IPasswordVaultMapper;
import top.aiolife.record.pojo.entity.PasswordVaultEntity;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PasswordVaultController 集成测试
 * 用于验证 SQL 脚本执行是否正确
 *
 * @author Lys
 * @date 2026/05/23
 */
class PasswordVaultControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private PasswordVaultController passwordVaultController;

    @Autowired
    private IPasswordVaultMapper passwordVaultMapper;

    @Test
    void testList_查询密码列表() {
        Long passwordId = System.currentTimeMillis() % 1000000 + 900000L;
        passwordVaultMapper.insert(createPassword(passwordId));

        var response = passwordVaultController.list(String.valueOf(TEST_USER_ID));
        assertSuccess(response);
        assertNotNull(response.getData());
    }

    @Test
    void testCategories_获取分类列表() {
        var response = passwordVaultController.categories(String.valueOf(TEST_USER_ID));
        assertSuccess(response);
        assertNotNull(response.getData());
    }

    private PasswordVaultEntity createPassword(Long passwordId) {
        PasswordVaultEntity entity = new PasswordVaultEntity();
        entity.setId(passwordId);
        entity.setUserId(TEST_USER_ID);
        entity.setTitle("测试密码");
        entity.setUsername("test@example.com");
        entity.setCategory("测试");
        entity.setSalt("testSalt123");
        entity.fillCreateCommonField(TEST_USER_ID);
        return entity;
    }
}
