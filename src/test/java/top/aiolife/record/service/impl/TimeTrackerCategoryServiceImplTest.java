package top.aiolife.record.service.impl;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import top.aiolife.record.pojo.entity.TimeTrackerCategoryEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class TimeTrackerCategoryServiceImplTest {

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant builderAssistant = new MapperBuilderAssistant(
                new MybatisConfiguration(), "TimeTrackerCategoryServiceImplTest");
        TableInfoHelper.initTableInfo(builderAssistant, TimeTrackerCategoryEntity.class);
    }

    @Test
    void overrideFields_shouldInsertExplicitNullInsteadOfDatabaseDefaults() throws NoSuchFieldException {
        for (String fieldName : new String[]{"isTrackTime", "sort", "isEnabled", "timeType"}) {
            TableField tableField = TimeTrackerCategoryEntity.class
                    .getDeclaredField(fieldName)
                    .getAnnotation(TableField.class);
            assertEquals(FieldStrategy.ALWAYS, tableField.insertStrategy());
        }
    }

    @Test
    void createCategory_shouldAppendAfterLastVisibleCategoryWhenSortIsMissing() {
        TimeTrackerCategoryServiceImpl service = spy(new TimeTrackerCategoryServiceImpl());
        TimeTrackerCategoryEntity lastCategory = new TimeTrackerCategoryEntity();
        lastCategory.setSort(999);
        TimeTrackerCategoryEntity newCategory = new TimeTrackerCategoryEntity();

        doReturn(List.of(lastCategory)).when(service).listUserVisibleCategories(13L);
        doReturn(true).when(service).save(any(TimeTrackerCategoryEntity.class));

        service.createCategory(newCategory, 13L);

        assertEquals(1009, newCategory.getSort());
        assertEquals(0, newCategory.getIsTrackTime());
        assertEquals(1, newCategory.getIsEnabled());
        assertEquals(1, newCategory.getTimeType());
    }

    @Test
    void adminCreateCategory_shouldAppendAfterLastPublicCategoryWhenSortIsMissing() {
        TimeTrackerCategoryServiceImpl service = spy(new TimeTrackerCategoryServiceImpl());
        TimeTrackerCategoryEntity lastCategory = new TimeTrackerCategoryEntity();
        lastCategory.setSort(140);
        TimeTrackerCategoryEntity newCategory = new TimeTrackerCategoryEntity();

        doReturn(List.of(lastCategory)).when(service).listAllCategories();
        doReturn(true).when(service).save(any(TimeTrackerCategoryEntity.class));

        service.adminCreateCategory(newCategory);

        assertEquals(150, newCategory.getSort());
        assertEquals(0, newCategory.getIsTrackTime());
        assertEquals(1, newCategory.getIsEnabled());
        assertEquals(1, newCategory.getTimeType());
    }

    @Test
    void updateCategory_shouldStoreOnlyChangedFieldsWhenCreatingPartialOverride() {
        TimeTrackerCategoryServiceImpl service = spy(new TimeTrackerCategoryServiceImpl());
        TimeTrackerCategoryEntity template = createPublicCategory();
        TimeTrackerCategoryEntity updates = new TimeTrackerCategoryEntity();
        updates.setIsTrackTime(0);

        doReturn(template).when(service).getById(template.getId());
        doReturn(null).when(service).getOne(any(Wrapper.class));
        doReturn(true).when(service).save(any(TimeTrackerCategoryEntity.class));

        service.updateCategory(template.getId(), updates, 13L);

        ArgumentCaptor<TimeTrackerCategoryEntity> captor = ArgumentCaptor.forClass(TimeTrackerCategoryEntity.class);
        verify(service).save(captor.capture());
        TimeTrackerCategoryEntity saved = captor.getValue();

        assertEquals(13L, saved.getUserId());
        assertEquals(template.getId(), saved.getTemplateId());
        assertNull(saved.getName());
        assertNull(saved.getColor());
        assertEquals(0, saved.getIsTrackTime());
        assertNull(saved.getSort());
        assertNull(saved.getTimeType());
        assertNull(saved.getIsEnabled());
        assertEquals(0, saved.getIsDeleted());
    }

    @Test
    void updateCategory_shouldKeepExplicitZeroSortWhenCreatingSortOverride() {
        TimeTrackerCategoryServiceImpl service = spy(new TimeTrackerCategoryServiceImpl());
        TimeTrackerCategoryEntity template = createPublicCategory();
        TimeTrackerCategoryEntity updates = new TimeTrackerCategoryEntity();
        updates.setSort(0);

        doReturn(template).when(service).getById(template.getId());
        doReturn(null).when(service).getOne(any(Wrapper.class));
        doReturn(true).when(service).save(any(TimeTrackerCategoryEntity.class));

        service.updateCategory(template.getId(), updates, 13L);

        ArgumentCaptor<TimeTrackerCategoryEntity> captor = ArgumentCaptor.forClass(TimeTrackerCategoryEntity.class);
        verify(service).save(captor.capture());
        assertEquals(0, captor.getValue().getSort());
        assertNull(captor.getValue().getTimeType());
    }

    @Test
    void updateCategory_shouldClearOverrideWhenValueMatchesTemplate() {
        TimeTrackerCategoryServiceImpl service = spy(new TimeTrackerCategoryServiceImpl());
        TimeTrackerCategoryEntity template = createPublicCategory();
        TimeTrackerCategoryEntity existingOverride = new TimeTrackerCategoryEntity();
        existingOverride.setId(99L);
        existingOverride.setTemplateId(template.getId());
        existingOverride.setSort(0);
        TimeTrackerCategoryEntity updates = new TimeTrackerCategoryEntity();
        updates.setSort(template.getSort());

        doReturn(template).when(service).getById(template.getId());
        doReturn(existingOverride).when(service).getOne(any(Wrapper.class));
        doReturn(true).when(service).update(any(Wrapper.class));

        service.updateCategory(template.getId(), updates, 13L);

        ArgumentCaptor<Wrapper<TimeTrackerCategoryEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(service).update(captor.capture());
        LambdaUpdateWrapper<TimeTrackerCategoryEntity> wrapper =
                (LambdaUpdateWrapper<TimeTrackerCategoryEntity>) captor.getValue();
        String sqlSet = wrapper.getSqlSet();
        assertTrue(sqlSet.contains("sort="));
        assertTrue(wrapper.getParamNameValuePairs().containsValue(null));
    }

    private TimeTrackerCategoryEntity createPublicCategory() {
        TimeTrackerCategoryEntity category = new TimeTrackerCategoryEntity();
        category.setId(5L);
        category.setUserId(0L);
        category.setName("娱乐");
        category.setColor("#eb2f96");
        category.setIcon("tabler:pacman");
        category.setDescription("休闲娱乐");
        category.setIsTrackTime(1);
        category.setSort(110);
        category.setIsEnabled(1);
        category.setTimeType(3);
        category.setIsDeleted(0);
        return category;
    }
}
