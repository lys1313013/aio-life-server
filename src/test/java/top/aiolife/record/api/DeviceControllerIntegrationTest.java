package top.aiolife.record.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import top.aiolife.core.query.CommonQuery;
import top.aiolife.record.mapper.IDeviceMapper;
import top.aiolife.record.pojo.entity.DeviceEntity;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DeviceController 集成测试
 * 用于验证 SQL 脚本执行是否正确
 *
 * @author Lys
 * @date 2026/05/23
 */
class DeviceControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private DeviceController deviceController;

    @Autowired
    private IDeviceMapper deviceMapper;

    @Test
    void testQuery_查询设备列表() {
        Long deviceId = System.currentTimeMillis() % 1000000 + 900000;
        deviceMapper.insert(createDevice(deviceId));

        CommonQuery<DeviceEntity> query = new CommonQuery<>();
        query.setPage(1);
        query.setPageSize(10);
        query.setCondition(new DeviceEntity());

        var response = deviceController.query(query);
        assertSuccess(response);
        assertNotNull(response.getData());
        assertTrue(response.getData().getTotal() >= 1);
    }

    private DeviceEntity createDevice(Long deviceId) {
        DeviceEntity entity = new DeviceEntity();
        entity.setId(deviceId);
        entity.setUserId(TEST_USER_ID);
        entity.setName("测试设备");
        entity.setType("测试类型");
        entity.setPurchaseDate(LocalDate.now());
        entity.setEndDate(LocalDate.now().plusDays(1));
        entity.setPurchasePrice(BigDecimal.valueOf(100));
        return entity;
    }
}
