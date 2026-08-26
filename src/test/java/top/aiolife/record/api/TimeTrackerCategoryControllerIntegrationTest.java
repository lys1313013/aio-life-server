package top.aiolife.record.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import top.aiolife.record.pojo.entity.TimeTrackerCategoryEntity;
import top.aiolife.record.service.ITimeTrackerCategoryService;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TimeTrackerCategoryController 集成测试
 * 用于验证 SQL 脚本执行是否正确
 *
 * @author Lys
 * @date 2026/05/23
 */
class TimeTrackerCategoryControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private TimeTrackerCategoryController timeTrackerCategoryController;

    @Autowired
    private ITimeTrackerCategoryService categoryService;

    @Test
    void testList_获取分类列表() {
        TimeTrackerCategoryEntity entity = createCategory();
        categoryService.adminCreateCategory(entity);

        var response = timeTrackerCategoryController.list();
        assertSuccess(response);
        assertNotNull(response.getData());
        assertTrue(response.getData().size() >= 1);
    }

    @Test
    void testListHidden_获取隐藏分类列表() {
        TimeTrackerCategoryEntity entity = createCategory();
        entity.setIsEnabled(0);
        categoryService.adminCreateCategory(entity);

        var response = timeTrackerCategoryController.listHidden();
        assertSuccess(response);
        assertNotNull(response.getData());
    }

    private TimeTrackerCategoryEntity createCategory() {
        TimeTrackerCategoryEntity entity = new TimeTrackerCategoryEntity();
        entity.setName("测试分类_" + System.currentTimeMillis());
        entity.setColor("#3498DB");
        entity.setIcon("mdi:test");
        entity.setIsEnabled(1);
        entity.setIsDeleted(0);
        entity.setSort(0);
        entity.setIsTrackTime(1);
        entity.setTimeType(1);
        entity.fillCreateCommonField(TEST_USER_ID);
        return entity;
    }
}
