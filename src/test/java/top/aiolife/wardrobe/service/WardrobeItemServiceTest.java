package top.aiolife.wardrobe.service;

import top.aiolife.record.service.RecordImageService;

import static top.aiolife.support.MockMatchers.anyWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import top.aiolife.wardrobe.mapper.WardrobeCategoryMapper;
import top.aiolife.wardrobe.mapper.WardrobeItemMapper;
import top.aiolife.wardrobe.pojo.entity.WardrobeCategoryEntity;
import top.aiolife.wardrobe.pojo.entity.WardrobeItemEntity;
import top.aiolife.wardrobe.service.impl.WardrobeItemServiceImpl;

import java.util.List;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WardrobeItemServiceTest {
    private WardrobeCategoryMapper categories;
    private WardrobeItemMapper items;
    private WardrobeItemServiceImpl service;

    @BeforeAll
    static void initializeTableMetadata() {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "wardrobe-item-service-test");
        TableInfoHelper.initTableInfo(assistant, WardrobeItemEntity.class);
        TableInfoHelper.initTableInfo(assistant, WardrobeCategoryEntity.class);
    }

    @BeforeEach
    void setUp() {
        categories = mock(WardrobeCategoryMapper.class);
        items = mock(WardrobeItemMapper.class);
        service = new WardrobeItemServiceImpl(categories, mock(RecordImageService.class));
        ReflectionTestUtils.setField(service, "baseMapper", items);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 100})
    void listItems_分类查询次数不随衣物数量增加(int count) {
        var records = LongStream.rangeClosed(1, count)
                .mapToObj(id -> item(id, 11L, id % 2 == 0 ? 22L : 21L)).toList();
        when(items.selectList(anyWrapper())).thenReturn(records);
        when(categories.selectList(anyWrapper()))
                .thenReturn(List.of(category(21L, "上装"), category(22L, "下装")));

        var result = service.listItems(11L, null, null, null);

        assertEquals(count, result.size());
        for (int index = 0; index < count; index++) {
            var row = result.get(index);
            assertEquals(records.get(index).getId(), row.getId());
            assertEquals(records.get(index).getName(), row.getName());
            assertEquals(index % 2 == 0 ? "上装" : "下装", row.getCategoryName());
        }
        verify(items, times(1)).selectList(anyWrapper());
        verify(categories, times(1)).selectList(anyWrapper());
    }

    @Test
    void listItems_空列表不查询分类() {
        when(items.selectList(anyWrapper())).thenReturn(List.of());

        assertTrue(service.listItems(11L, null, null, null).isEmpty());

        verifyNoInteractions(categories);
    }

    @Test
    void listItems_不同请求重新加载分类且缺失分类保留空名称() {
        when(items.selectList(anyWrapper()))
                .thenReturn(List.of(item(1L, 11L, 21L), item(2L, 11L, 99L)))
                .thenReturn(List.of(item(3L, 12L, 22L)));
        when(categories.selectList(anyWrapper()))
                .thenReturn(List.of(category(21L, "用户甲分类"))).thenReturn(List.of(category(22L, "用户乙分类")));

        var first = service.listItems(11L, null, null, null);
        var second = service.listItems(12L, null, null, null);

        assertEquals("用户甲分类", first.getFirst().getCategoryName());
        assertNull(first.get(1).getCategoryName());
        assertEquals("用户乙分类", second.getFirst().getCategoryName());
        verify(categories, times(2)).selectList(anyWrapper());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getItem_详情仍返回分类名称且不存在时不查询分类() {
        var target = spy(service);
        LambdaQueryChainWrapper<WardrobeItemEntity> query = mock(LambdaQueryChainWrapper.class, RETURNS_SELF);
        doReturn(query).when(target).lambdaQuery();
        when(query.eq(any(), any())).thenReturn(query);
        when(query.one()).thenReturn(item(1L, 11L, 21L)).thenReturn(null);
        when(categories.selectList(anyWrapper())).thenReturn(List.of(category(21L, "上装")));

        assertEquals("上装", target.getItem(1L, 11L).getCategoryName());
        assertNull(target.getItem(2L, 11L));
        verify(categories, times(1)).selectList(anyWrapper());
    }

    private WardrobeItemEntity item(long id, long userId, long categoryId) {
        var item = new WardrobeItemEntity();
        item.setId(id);
        item.setUserId(userId);
        item.setCategoryId(categoryId);
        item.setName("衣物" + id);
        return item;
    }

    private WardrobeCategoryEntity category(long id, String name) {
        var category = new WardrobeCategoryEntity();
        category.setId(id);
        category.setName(name);
        return category;
    }
}
