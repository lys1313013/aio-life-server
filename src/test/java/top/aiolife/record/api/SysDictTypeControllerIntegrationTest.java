package top.aiolife.record.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import top.aiolife.record.mapper.ISysDictTypeMapper;
import top.aiolife.record.pojo.entity.SysDictTypeEntity;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SysDictTypeController 集成测试
 * 用于验证 SQL 脚本执行是否正确
 *
 * @author Lys
 * @date 2026/05/23
 */
class SysDictTypeControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private SysDictTypeController sysDictTypeController;

    @Autowired
    private ISysDictTypeMapper sysDictTypeMapper;

    @Test
    void testGetByDictType_根据类型查询字典() {
        Long id = System.currentTimeMillis() % 1000000 + 900000L;
        String dictType = "test_type_" + id;
        sysDictTypeMapper.insert(createSysDictType(id, dictType));

        var response = sysDictTypeController.queryById(dictType);
        assertSuccess(response);
        assertNotNull(response.getData());
        assertEquals(dictType, response.getData().getSysDictTypeEntity().getDictType());
    }

    private SysDictTypeEntity createSysDictType(Long id, String dictType) {
        SysDictTypeEntity entity = new SysDictTypeEntity();
        entity.setId(id);
        entity.setDictName("测试字典");
        entity.setDictType(dictType);
        entity.setIsEnabled(1);
        return entity;
    }
}
