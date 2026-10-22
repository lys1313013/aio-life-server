package top.aiolife.database;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import top.aiolife.system.mapper.SystemRegionMapper;
import top.aiolife.system.pojo.entity.SystemRegionEntity;
import top.aiolife.system.service.SystemRegionService;

import static org.junit.jupiter.api.Assertions.*;

/** 真实MySQL目录及层级契约；测试写入由事务回滚。 */
@ActiveProfiles("test")
@SpringBootTest(classes = PhysicalTableEntitySelectIntegrationTest.DatabaseMapperTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class SystemRegionIntegrationTest {
    @Autowired private SystemRegionMapper mapper;

    @Test void 四级数据按父级加载并恢复直辖市完整路径() {
        var service = new SystemRegionService(mapper);
        assertEquals(31, service.children(null).size());
        var provinces = service.children("");
        assertEquals("11", provinces.getFirst().code());
        assertTrue(provinces.getFirst().hasChildren());
        var path = service.path("110101001");
        assertEquals(List.of("11", "1101", "110101", "110101001"), path.stream().map(item -> item.code()).toList());
        assertEquals(List.of(1, 2, 3, 4), path.stream().map(item -> item.level()).toList());
        assertEquals("东华门街道", path.getLast().name());
        assertFalse(path.getLast().hasChildren());
        assertTrue(service.children("110101").stream().anyMatch(item -> item.code().equals("110101001")));
        assertTrue(service.children("110101001").isEmpty());
        assertDoesNotThrow(() -> service.validateSelection(null));
        assertDoesNotThrow(() -> service.validateSelection("11"));
        assertThrows(IllegalArgumentException.class, () -> service.children("11 OR 1=1"));
        assertThrows(ResponseStatusException.class, () -> service.path("999999999"));
    }

    @Test void 停用地区不会出现在列表且不能写入新选择() {
        var service = new SystemRegionService(mapper);
        var item = mapper.selectOne(new LambdaQueryWrapper<SystemRegionEntity>().eq(SystemRegionEntity::getCode, "110101001"));
        assertNotNull(item);
        mapper.deleteById(item.getId());
        assertFalse(service.children("110101").stream().anyMatch(row -> row.code().equals("110101001")));
        assertThrows(ResponseStatusException.class, () -> service.validateSelection("110101001"));
    }
}
