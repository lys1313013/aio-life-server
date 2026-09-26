package top.aiolife.security;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import top.aiolife.record.pojo.entity.TimeRecordEntity;
import top.aiolife.record.pojo.entity.TimeTrackerCategoryEntity;
import top.aiolife.record.pojo.req.TimeRecordReq;
import top.aiolife.record.service.ITimeTrackerCategoryService;
import top.aiolife.record.service.impl.TimeRecordServiceImpl;
import top.aiolife.sso.service.SecondaryLockGuard;
import top.aiolife.wardrobe.mapper.WardrobeCategoryMapper;
import top.aiolife.wardrobe.pojo.entity.WardrobeCategoryEntity;
import top.aiolife.wardrobe.pojo.entity.WardrobeItemEntity;
import top.aiolife.wardrobe.pojo.req.WardrobeItemReq;
import top.aiolife.wardrobe.service.impl.WardrobeItemServiceImpl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class CategoryOwnershipTest {
    @BeforeAll
    static void tables() {
        for (Class<?> type : java.util.List.of(TimeTrackerCategoryEntity.class, WardrobeCategoryEntity.class)) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), type.getName()), type);
        }
    }

    @Test
    void foreignTimeCategory_isRejectedBeforeRecordSave() {
        var categories = mock(ITimeTrackerCategoryService.class);
        var service = spy(new TimeRecordServiceImpl(null, null, null, null, null, null, null,
                categories, mock(SecondaryLockGuard.class)));
        var req = new TimeRecordReq(); req.setCategoryId("22");
        try (var login = mockStatic(StpUtil.class)) {
            login.when(StpUtil::getLoginIdAsLong).thenReturn(11L);
            assertThrows(IllegalArgumentException.class, () -> service.saveTimeRecord(req));
            verify(service, never()).save(any(TimeRecordEntity.class));
            var query = ArgumentCaptor.forClass(Wrapper.class);
            verify(categories).count(query.capture());
            String sql = query.getValue().getSqlSegment();
            assertTrue(sql.contains("user_id")); assertTrue(sql.contains(" OR "));
        }
    }

    @Test
    void allowedTimeCategory_preservesNormalSave() {
        var categories = mock(ITimeTrackerCategoryService.class);
        when(categories.count(any(Wrapper.class))).thenReturn(1L);
        var visible = new TimeTrackerCategoryEntity(); visible.setId(22L);
        when(categories.listUserVisibleCategories(11L)).thenReturn(java.util.List.of(visible));
        var service = spy(new TimeRecordServiceImpl(null, null, null, null, null, null, null,
                categories, mock(SecondaryLockGuard.class)));
        doAnswer(call -> {
            TimeRecordEntity entity = call.getArgument(0);
            assertEquals(11L, entity.getUserId()); entity.setId("7"); return true;
        }).when(service).save(any(TimeRecordEntity.class));
        var req = new TimeRecordReq(); req.setCategoryId("22");
        try (var login = mockStatic(StpUtil.class)) {
            login.when(StpUtil::getLoginIdAsLong).thenReturn(11L);
            assertEquals("7", service.saveTimeRecord(req));
        }
    }

    @Test
    void foreignWardrobeCategory_isRejectedForCreateAndUpdate() {
        var categories = mock(WardrobeCategoryMapper.class);
        var service = spy(new WardrobeItemServiceImpl(categories));
        var req = new WardrobeItemReq(); req.setCategoryId(22L); req.setId(7L);
        var existing = new WardrobeItemEntity(); existing.setUserId(11L);
        doReturn(existing).when(service).getById(7L);
        try (var login = mockStatic(StpUtil.class)) {
            login.when(StpUtil::getLoginIdAsLong).thenReturn(11L);
            assertThrows(IllegalArgumentException.class, () -> service.saveItem(req));
            assertThrows(IllegalArgumentException.class, () -> service.updateItem(req));
            verify(service, never()).save(any(WardrobeItemEntity.class));
            verify(service, never()).updateById(any(WardrobeItemEntity.class));
        }
    }
}
