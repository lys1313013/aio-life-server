package top.aiolife.record.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import top.aiolife.record.mapper.ITimeRecordMapper;
import top.aiolife.record.pojo.entity.TimeRecordEntity;
import top.aiolife.record.pojo.entity.TimeTrackerCategoryEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TimeCategoryHierarchyTest {
    @BeforeAll
    static void tables() {
        for (var type : List.of(TimeTrackerCategoryEntity.class, TimeRecordEntity.class)) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), type.getName()), type);
        }
    }

    private TimeTrackerCategoryEntity category(long id, long owner, Long parent) {
        var c = new TimeTrackerCategoryEntity();
        c.setId(id); c.setUserId(owner); c.setParentId(parent);
        c.setName("分类" + id); c.setIsDeleted(0); c.setIsEnabled(1);
        c.setSort(0); c.setTimeType(1); c.setIsTrackTime(0);
        return c;
    }

    @Test
    void 父级校验拒绝三级自引用循环和不可见分类() {
        var service = new TimeTrackerCategoryServiceImpl();
        var tree = List.of(category(1, 0, 0L), category(2, 0, 1L), category(3, 8, 0L));
        assertDoesNotThrow(() -> service.validateParent(3L, 1L, tree));
        assertThrows(IllegalArgumentException.class, () -> service.validateParent(3L, 2L, tree));
        assertThrows(IllegalArgumentException.class, () -> service.validateParent(1L, 1L, tree));
        assertThrows(IllegalArgumentException.class, () -> service.validateParent(1L, 2L, tree));
        assertThrows(IllegalArgumentException.class, () -> service.validateParent(1L, 3L, tree));
        assertThrows(IllegalArgumentException.class, () -> service.validateParent(3L, 999L, tree));
        assertDoesNotThrow(() -> service.validateParent(2L, 0L, tree));
    }

    @Test
    void 新增二级分类只接在同级排序之后() {
        var service = spy(new TimeTrackerCategoryServiceImpl());
        doNothing().when(service).lockHierarchy();
        var root = category(1, 0, 0L); root.setSort(900);
        var child = category(2, 8, 1L); child.setSort(20);
        doReturn(List.of(root, child)).when(service).listUserCategories(8L);
        doReturn(List.of(root, child)).when(service).listUserVisibleCategories(8L);
        doReturn(true).when(service).save(any(TimeTrackerCategoryEntity.class));
        var created = category(9, 8, 1L); created.setSort(null);
        service.createCategory(created, 8L);
        assertEquals(30, created.getSort());
        assertEquals(1L, created.getParentId());
        assertNull(created.getId());
    }

    @Test
    void 公共覆盖继承父级并始终使用公共ID() {
        var service = spy(new TimeTrackerCategoryServiceImpl());
        doNothing().when(service).lockHierarchy();
        var override = category(100, 8, null); override.setTemplateId(2L); override.setName("个人名称");
        doReturn(List.of(category(1, 0, 0L), category(2, 0, 1L)), List.of(override))
                .when(service).list(any(Wrapper.class));
        var merged = service.listUserCategories(8L).stream().filter(c -> c.getId() == 2L).findFirst().orElseThrow();
        assertEquals(1L, merged.getParentId());
        assertEquals(2L, merged.getTemplateId());
        assertEquals("个人名称", merged.getName());
    }

    @Test
    void 覆盖写零可以移到一级且隐藏父级保留历史元数据() {
        var service = spy(new TimeTrackerCategoryServiceImpl());
        doNothing().when(service).lockHierarchy();
        var override = category(100, 8, 0L); override.setTemplateId(2L);
        var hidden = category(101, 8, null); hidden.setTemplateId(1L); hidden.setIsEnabled(0);
        doReturn(List.of(category(1, 0, 0L), category(2, 0, 1L), category(3, 0, 1L)), List.of(override, hidden))
                .when(service).list(any(Wrapper.class));
        var all = service.listUserCategories(8L);
        assertEquals(3, all.size());
        assertEquals(0, all.stream().filter(c -> c.getId() == 3L).findFirst().orElseThrow().getIsEnabled());
        assertEquals(1, all.stream().filter(c -> c.getId() == 2L).findFirst().orElseThrow().getIsEnabled());
    }

    @Test
    void 接口区分省略父级和显式空值且不接受标志注入() throws Exception {
        var mapper = new ObjectMapper();
        assertFalse(mapper.readValue("{\"name\":\"改名\"}", TimeTrackerCategoryEntity.class).isParentIdSpecified());
        var reset = mapper.readValue("{\"parentId\":null}", TimeTrackerCategoryEntity.class);
        assertTrue(reset.isParentIdSpecified()); assertNull(reset.getParentId());
        assertFalse(mapper.readValue("{\"parentIdSpecified\":true}", TimeTrackerCategoryEntity.class).isParentIdSpecified());
        assertFalse(mapper.writeValueAsString(reset).contains("parentIdSpecified"));
    }

    @Test
    void 显式空值恢复公共归属并真正写入数据库NULL() {
        var service = spy(new TimeTrackerCategoryServiceImpl());
        doNothing().when(service).lockHierarchy();
        var template = category(2, 0, 1L);
        var override = category(100, 8, 0L); override.setTemplateId(2L);
        doReturn(template).when(service).getById(2L);
        doReturn(override).when(service).getOne(any(Wrapper.class));
        doReturn(List.of(category(1, 0, 0L), category(2, 8, 0L))).when(service).listUserCategories(8L);
        doReturn(true).when(service).update(any(Wrapper.class));
        var updates = new TimeTrackerCategoryEntity(); updates.setParentId(null);
        service.updateCategory(2L, updates, 8L);
        var capture = ArgumentCaptor.forClass(Wrapper.class);
        verify(service).update(capture.capture());
        var wrapper = (LambdaUpdateWrapper<?>) capture.getValue();
        assertTrue(wrapper.getSqlSet().contains("parent_id="));
        assertTrue(wrapper.getParamNameValuePairs().containsValue(null));
    }

    @Test
    void 管理员移动公共分类也检查用户私有子分类() {
        var service = spy(new TimeTrackerCategoryServiceImpl());
        doNothing().when(service).lockHierarchy();
        doReturn(category(1, 0, 0L)).when(service).getById(1L);
        doReturn(List.of(category(1, 0, 0L), category(2, 0, 0L))).when(service).listAllCategories();
        doReturn(List.of(category(3, 8, 1L)), List.of()).when(service).list(any(Wrapper.class));
        doReturn(List.of(category(1, 0, 0L), category(2, 0, 0L), category(3, 8, 1L)))
                .when(service).listUserCategories(8L);
        var updates = new TimeTrackerCategoryEntity(); updates.setParentId(2L);
        assertThrows(IllegalArgumentException.class, () -> service.adminUpdateCategory(1L, updates));
        verify(service, never()).updateById(any());
    }

    @Test
    void 新增公共子分类拒绝用户已将其上级移动为二级的情况() {
        var service = spy(new TimeTrackerCategoryServiceImpl());
        doNothing().when(service).lockHierarchy();
        doReturn(List.of(category(1, 0, 0L))).when(service).listAllCategories();
        doReturn(List.of(category(9, 8, 0L))).when(service).list(any(Wrapper.class));
        doReturn(List.of(category(1, 0, 9L), category(9, 8, 0L))).when(service).listUserCategories(8L);
        var child = category(3, 0, 1L);
        assertThrows(IllegalArgumentException.class, () -> service.adminCreateCategory(child));
        verify(service, never()).save(any(TimeTrackerCategoryEntity.class));
    }

    @Test
    void 有子分类或记录时拒绝删除() {
        var service = spy(new TimeTrackerCategoryServiceImpl());
        doNothing().when(service).lockHierarchy();
        doReturn(category(1, 8, 0L)).when(service).getById(1L);
        doReturn(1L).when(service).count(any(Wrapper.class));
        assertThrows(IllegalArgumentException.class, () -> service.deleteCategory(1L, 8L));
        verify(service, never()).updateById(any());
        doReturn(0L).when(service).count(any(Wrapper.class));
        var records = mock(ITimeRecordMapper.class);
        ReflectionTestUtils.setField(service, "timeRecordMapper", records);
        when(records.selectCount(any(Wrapper.class))).thenReturn(1L);
        assertThrows(IllegalArgumentException.class, () -> service.deleteCategory(1L, 8L));
        verify(service, never()).updateById(any());
    }
}
