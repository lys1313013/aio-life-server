package top.aiolife.wardrobe.service;

import static top.aiolife.support.MockMatchers.anyWrapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import top.aiolife.wardrobe.mapper.WardrobeCategoryMapper;
import top.aiolife.wardrobe.mapper.WardrobeItemMapper;
import top.aiolife.wardrobe.pojo.entity.WardrobeCategoryEntity;
import top.aiolife.wardrobe.pojo.entity.WardrobeItemEntity;
import top.aiolife.wardrobe.pojo.req.CategoryReq;
import top.aiolife.wardrobe.service.impl.WardrobeCategoryServiceImpl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WardrobeCategoryServiceTest {

    private WardrobeCategoryMapper categories;
    private WardrobeItemMapper items;
    private WardrobeCategoryServiceImpl service;

    @BeforeAll
    static void initializeTableMetadata() {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "wardrobe-category-service-test");
        TableInfoHelper.initTableInfo(assistant, WardrobeCategoryEntity.class);
        TableInfoHelper.initTableInfo(assistant, WardrobeItemEntity.class);
    }

    @BeforeEach
    void setUp() {
        categories = mock(WardrobeCategoryMapper.class);
        items = mock(WardrobeItemMapper.class);
        service = new WardrobeCategoryServiceImpl(items);
        ReflectionTestUtils.setField(service, "baseMapper", categories);
    }

    @Test
    void removeCategory_系统预设分类不可删除() {
        when(categories.selectById(1L)).thenReturn(category(1L, null, null));

        var error = assertThrows(IllegalArgumentException.class, () -> service.removeCategory(1L, 11L));

        assertEquals("系统预设分类不可删除", error.getMessage());
        verify(categories, never()).deleteById(1L);
        verifyNoInteractions(items);
    }

    @Test
    void removeCategory_无权删除其他用户分类() {
        when(categories.selectById(1L)).thenReturn(category(1L, 12L, null));

        var error = assertThrows(IllegalArgumentException.class, () -> service.removeCategory(1L, 11L));

        assertEquals("无权删除此分类", error.getMessage());
        verify(categories, never()).deleteById(1L);
    }

    @Test
    void removeCategory_存在子分类时拒绝删除() {
        when(categories.selectById(1L)).thenReturn(category(1L, 11L, null));
        when(categories.selectCount(anyWrapper())).thenReturn(1L);

        var error = assertThrows(IllegalArgumentException.class, () -> service.removeCategory(1L, 11L));

        assertEquals("请先移动或删除子分类", error.getMessage());
        verifyNoInteractions(items);
        verify(categories, never()).deleteById(1L);
    }

    @Test
    void removeCategory_仍有关联衣物时拒绝删除() {
        when(categories.selectById(1L)).thenReturn(category(1L, 11L, null));
        when(categories.selectCount(anyWrapper())).thenReturn(0L);
        when(items.selectCount(anyWrapper())).thenReturn(1L);

        var error = assertThrows(IllegalArgumentException.class, () -> service.removeCategory(1L, 11L));

        assertEquals("该分类仍有关联衣物，请先转移衣物", error.getMessage());
        verify(categories, never()).deleteById(1L);
    }

    @Test
    void removeCategory_无子分类且无衣物时删除() {
        when(categories.selectById(1L)).thenReturn(category(1L, 11L, null));
        when(categories.selectCount(anyWrapper())).thenReturn(0L);
        when(items.selectCount(anyWrapper())).thenReturn(0L);

        service.removeCategory(1L, 11L);

        verify(categories).deleteById(1L);
    }

    @Test
    void saveCategory_上级分类不存在或不可见时拒绝() {
        when(categories.selectList(anyWrapper())).thenReturn(List.of());

        var error = assertThrows(IllegalArgumentException.class,
                () -> service.saveCategory(11L, request("运动", 99L)));

        assertEquals("上级分类不存在或无权使用", error.getMessage());
        verify(categories, never()).insert(any(WardrobeCategoryEntity.class));
    }

    @Test
    void saveCategory_上级分类已有父级时拒绝第三层() {
        when(categories.selectList(anyWrapper())).thenReturn(List.of(category(50L, 11L, 40L)));

        var error = assertThrows(IllegalArgumentException.class,
                () -> service.saveCategory(11L, request("运动", 50L)));

        assertEquals("分类最多支持两级，请选择一级分类作为上级", error.getMessage());
        verify(categories, never()).insert(any(WardrobeCategoryEntity.class));
    }

    @Test
    void saveCategory_合法一级分类作为上级时保存() {
        when(categories.selectList(anyWrapper())).thenReturn(List.of(category(50L, null, null)));

        service.saveCategory(11L, request("运动", 50L));

        verify(categories).insert(any(WardrobeCategoryEntity.class));
    }

    @Test
    void updateCategory_有子分类时不能移动到其他分类下() {
        when(categories.selectById(1L)).thenReturn(category(1L, 11L, null));
        when(categories.selectList(anyWrapper())).thenReturn(List.of(category(50L, 11L, null)));
        when(categories.selectCount(anyWrapper())).thenReturn(1L);

        var error = assertThrows(IllegalArgumentException.class,
                () -> service.updateCategory(11L, request(1L, "上装", 50L)));

        assertEquals("该分类已有子分类，不能移动到其他分类下", error.getMessage());
        verify(categories, never()).updateById(any(WardrobeCategoryEntity.class));
    }

    @Test
    void updateCategory_不能把自身设为上级() {
        when(categories.selectById(1L)).thenReturn(category(1L, 11L, null));

        var error = assertThrows(IllegalArgumentException.class,
                () -> service.updateCategory(11L, request(1L, "上装", 1L)));

        assertEquals("分类不能作为自己的上级", error.getMessage());
        verifyNoInteractions(items);
    }

    @Test
    void updateCategory_合法移动到一级分类下时更新() {
        when(categories.selectById(1L)).thenReturn(category(1L, 11L, null));
        when(categories.selectList(anyWrapper())).thenReturn(List.of(category(50L, 11L, null)));
        when(categories.selectCount(anyWrapper())).thenReturn(0L);

        service.updateCategory(11L, request(1L, "上装", 50L));

        verify(categories).updateById(any(WardrobeCategoryEntity.class));
    }

    @Test
    void listCategories_第三层节点不再被丢弃() {
        when(categories.selectList(anyWrapper())).thenReturn(List.of(
                category(1L, 11L, null),
                category(2L, 11L, 1L),
                category(3L, 11L, 2L)));

        var tree = service.listCategories(11L);

        assertEquals(1, tree.size());
        assertEquals(2L, tree.getFirst().getChildren().getFirst().getId());
        assertEquals(3L, tree.getFirst().getChildren().getFirst().getChildren().getFirst().getId());
    }

    @Test
    void listCategories_父级不可见的节点按根节点展示() {
        when(categories.selectList(anyWrapper())).thenReturn(List.of(category(2L, 11L, 99L)));

        var tree = service.listCategories(11L);

        assertEquals(1, tree.size());
        assertEquals(2L, tree.getFirst().getId());
        assertTrue(tree.getFirst().getChildren().isEmpty());
    }

    private CategoryReq request(String name, Long parentId) {
        return request(null, name, parentId);
    }

    private CategoryReq request(Long id, String name, Long parentId) {
        var req = new CategoryReq();
        req.setId(id);
        req.setName(name);
        req.setParentId(parentId);
        return req;
    }

    private WardrobeCategoryEntity category(long id, Long userId, Long parentId) {
        var category = new WardrobeCategoryEntity();
        category.setId(id);
        category.setUserId(userId);
        category.setParentId(parentId);
        category.setName("分类" + id);
        return category;
    }
}