package top.aiolife.record.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.aiolife.record.api.TimeTrackerCategoryController;
import top.aiolife.record.mapper.ITimeRecordMapper;
import top.aiolife.record.pojo.entity.TimeRecordEntity;
import top.aiolife.record.pojo.entity.TimeTrackerCategoryEntity;
import top.aiolife.record.pojo.req.TimeTrackerCategorySortReq;
import top.aiolife.record.service.TimeTrackerCategoryCache;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TimeTrackerCategoryCacheInvalidationTest {
    private static final long USER_ID = 13L;
    private TimeTrackerCategoryCache cache;
    private TimeTrackerCategoryServiceImpl service;

    @BeforeAll
    static void tables() {
        for (var type : List.of(TimeTrackerCategoryEntity.class, TimeRecordEntity.class)) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), type.getName()), type);
        }
    }

    @BeforeEach
    void setup() {
        cache = mock(TimeTrackerCategoryCache.class);
        when(cache.list(anyLong(), any())).thenAnswer(invocation ->
                invocation.<Supplier<List<TimeTrackerCategoryEntity>>>getArgument(1).get());
        service = spy(new TimeTrackerCategoryServiceImpl(cache));
        doNothing().when(service).lockHierarchy();
        doReturn(0L).when(service).count(any(Wrapper.class));
        var records = mock(ITimeRecordMapper.class);
        when(records.selectCount(any(Wrapper.class))).thenReturn(0L);
        ReflectionTestUtils.setField(service, "timeRecordMapper", records);
    }

    @Test
    void testRead_公共和个人分别缓存并保留覆盖与隐藏语义() {
        var publicCategory = category(5L, 0L);
        var hiddenOverride = category(99L, USER_ID);
        hiddenOverride.setTemplateId(5L);
        hiddenOverride.setIsEnabled(0);
        var personalCategory = category(7L, USER_ID);
        doReturn(List.of(publicCategory), List.of(hiddenOverride, personalCategory))
                .when(service).list(any(Wrapper.class));

        var result = service.listUserVisibleCategories(USER_ID);

        assertEquals(List.of(7L), result.stream().map(TimeTrackerCategoryEntity::getId).toList());
        verify(cache).list(eq(0L), any());
        verify(cache).list(eq(USER_ID), any());
        verify(cache, never()).evictAfterCommit(anyLong());
    }

    @Test
    void testTransaction_普通列表可缓存且写事务内计算保持事务边界() {
        var transactionStates = new ArrayList<Boolean>();
        doAnswer(invocation -> {
            transactionStates.add(TransactionSynchronizationManager.isActualTransactionActive());
            return invocation.<Supplier<List<TimeTrackerCategoryEntity>>>getArgument(1).get();
        }).when(cache).list(anyLong(), any());
        doReturn(List.of()).when(service).list(any(Wrapper.class));
        doReturn(true).when(service).save(any(TimeTrackerCategoryEntity.class));
        var interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(new TestTransactionManager());
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        var proxyFactory = new ProxyFactory(service);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvice(interceptor);
        var transactionalService = (TimeTrackerCategoryServiceImpl) proxyFactory.getProxy();

        transactionalService.listUserVisibleCategories(USER_ID);
        transactionalService.listUserCategories(USER_ID);
        var created = category(null, USER_ID);
        created.setSortOrder(null); // 新增分类在事务内读取分类，计算默认排序。
        transactionalService.createCategory(created, USER_ID);

        assertEquals(List.of(false, false, false, false, true, true), transactionStates);
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertFalse(TransactionSynchronizationManager.isSynchronizationActive());
    }

    @Test
    void testCreate_个人新增仅清本人缓存() {
        doReturn(true).when(service).save(any(TimeTrackerCategoryEntity.class));

        service.createCategory(category(null, USER_ID), USER_ID);

        assertOnlyInvalidated(USER_ID);
    }

    @Test
    void testUpdate_个人修改仅清本人缓存() {
        doReturn(category(7L, USER_ID)).when(service).getById(7L);
        doReturn(true).when(service).update(any(TimeTrackerCategoryEntity.class), any(Wrapper.class));
        var updates = new TimeTrackerCategoryEntity();
        updates.setName("新名称");

        service.updateCategory(7L, updates, USER_ID);

        assertOnlyInvalidated(USER_ID);
    }

    @Test
    void testDelete_个人删除仅清本人缓存() {
        doReturn(category(7L, USER_ID)).when(service).getById(7L);
        doReturn(true).when(service).updateById(any(TimeTrackerCategoryEntity.class));

        service.deleteCategory(7L, USER_ID);

        assertOnlyInvalidated(USER_ID);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testOverride_创建或更新公共覆盖仅清本人缓存(boolean alreadyOverridden) {
        doReturn(category(5L, 0L)).when(service).getById(5L);
        doReturn(alreadyOverridden ? category(99L, USER_ID) : null).when(service).getOne(any(Wrapper.class));
        doReturn(true).when(service).save(any(TimeTrackerCategoryEntity.class));
        doReturn(true).when(service).update(any(Wrapper.class));
        var updates = new TimeTrackerCategoryEntity();
        updates.setIsEnabled(1);

        service.updateCategory(5L, updates, USER_ID);

        assertOnlyInvalidated(USER_ID);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testHide_新增或更新隐藏记录仅清本人缓存(boolean alreadyOverridden) {
        doReturn(category(5L, 0L)).when(service).getById(5L);
        doReturn(alreadyOverridden ? category(99L, USER_ID) : null).when(service).getOne(any(Wrapper.class));
        doReturn(true).when(service).save(any(TimeTrackerCategoryEntity.class));
        doReturn(true).when(service).updateById(any(TimeTrackerCategoryEntity.class));

        service.deleteCategory(5L, USER_ID);

        assertOnlyInvalidated(USER_ID);
    }

    @Test
    void testSort_接口排序个人和公共覆盖均清本人缓存() {
        doReturn(category(7L, USER_ID)).when(service).getById(7L);
        doReturn(category(5L, 0L)).when(service).getById(5L);
        doReturn(null).when(service).getOne(any(Wrapper.class));
        doReturn(true).when(service).save(any(TimeTrackerCategoryEntity.class));
        doReturn(true).when(service).update(any(TimeTrackerCategoryEntity.class), any(Wrapper.class));
        var personal = new TimeTrackerCategorySortReq();
        personal.setId(7L);
        personal.setSortOrder(20);
        var shared = new TimeTrackerCategorySortReq();
        shared.setId(99L);
        shared.setTemplateId(5L);
        shared.setSortOrder(10);

        try (var login = mockStatic(StpUtil.class)) {
            login.when(StpUtil::getLoginIdAsLong).thenReturn(USER_ID);
            assertEquals(0, new TimeTrackerCategoryController(service).reSort(List.of(personal, shared)).getCode());
        }

        verify(service).updateCategory(eq(7L), argThat(c -> c.getSortOrder() == 20), eq(USER_ID));
        verify(service).updateCategory(eq(5L), argThat(c -> c.getSortOrder() == 10), eq(USER_ID));
        verify(cache, times(2)).evictAfterCommit(USER_ID);
        verifyNoMoreInteractions(cache);
    }

    @Test
    void testAdmin_新增修改和删除均清共享缓存() {
        doReturn(List.of()).when(service).listAllCategories();
        doReturn(true).when(service).save(any(TimeTrackerCategoryEntity.class));
        service.adminCreateCategory(category(null, 0L));
        assertOnlyInvalidated(0L);

        clearInvocations(cache);
        doReturn(category(5L, 0L)).when(service).getById(5L);
        doReturn(true).when(service).updateById(any(TimeTrackerCategoryEntity.class));
        var updates = new TimeTrackerCategoryEntity();
        updates.setSortOrder(30);
        service.adminUpdateCategory(5L, updates);
        assertOnlyInvalidated(0L);

        clearInvocations(cache);
        doReturn(true).when(service).removeById(5L);
        doReturn(true).when(service).remove(any(Wrapper.class));
        service.adminDeleteCategory(5L);
        assertOnlyInvalidated(0L);
    }

    @Test
    void testRejected_越权或分类校验失败不清缓存() {
        doReturn(category(7L, 99L)).when(service).getById(7L);
        assertThrows(IllegalArgumentException.class,
                () -> service.updateCategory(7L, new TimeTrackerCategoryEntity(), USER_ID));
        assertThrows(RuntimeException.class, () -> service.deleteCategory(7L, USER_ID));
        assertThrows(RuntimeException.class,
                () -> service.adminUpdateCategory(7L, new TimeTrackerCategoryEntity()));
        assertThrows(RuntimeException.class, () -> service.adminDeleteCategory(7L));
        doReturn(category(7L, USER_ID)).when(service).getById(7L);
        doReturn(1L).when(service).count(any(Wrapper.class));
        assertThrows(IllegalArgumentException.class, () -> service.deleteCategory(7L, USER_ID));
        verifyNoInteractions(cache);
    }

    @Test
    void testNoWrite_数据库未写入时保留缓存() {
        doReturn(false).when(service).save(any(TimeTrackerCategoryEntity.class));
        doReturn(false).when(service).update(any(TimeTrackerCategoryEntity.class), any(Wrapper.class));
        doReturn(false).when(service).update(any(Wrapper.class));
        doReturn(false).when(service).updateById(any(TimeTrackerCategoryEntity.class));
        doReturn(false).when(service).removeById(anyLong());
        doReturn(false).when(service).remove(any(Wrapper.class));
        doReturn(List.of()).when(service).listAllCategories();
        doReturn(category(7L, USER_ID)).when(service).getById(7L);
        doReturn(category(5L, 0L)).when(service).getById(5L);
        doReturn(null).when(service).getOne(any(Wrapper.class));
        service.createCategory(category(null, USER_ID), USER_ID);
        service.updateCategory(7L, new TimeTrackerCategoryEntity(), USER_ID);
        service.deleteCategory(7L, USER_ID);
        service.updateCategory(5L, new TimeTrackerCategoryEntity(), USER_ID);
        service.deleteCategory(5L, USER_ID);
        doReturn(category(99L, USER_ID)).when(service).getOne(any(Wrapper.class));
        service.updateCategory(5L, new TimeTrackerCategoryEntity(), USER_ID);
        service.deleteCategory(5L, USER_ID);
        service.adminCreateCategory(category(null, 0L));
        service.adminUpdateCategory(5L, new TimeTrackerCategoryEntity());
        service.adminDeleteCategory(5L);
        verifyNoInteractions(cache);
    }

    private void assertOnlyInvalidated(long ownerId) {
        verify(cache).evictAfterCommit(ownerId);
        verifyNoMoreInteractions(cache);
    }

    private TimeTrackerCategoryEntity category(Long id, long ownerId) {
        var row = new TimeTrackerCategoryEntity();
        row.setId(id);
        row.setUserId(ownerId);
        row.setName("分类" + id);
        row.setSortOrder(0);
        row.setIsEnabled(1);
        row.setIsDeleted(0);
        row.setIsTrackTime(1);
        row.setTimeType(1);
        return row;
    }

    private static class TestTransactionManager extends AbstractPlatformTransactionManager {
        @Override
        protected Object doGetTransaction() { return new Object(); }
        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) { }
        @Override
        protected void doCommit(DefaultTransactionStatus status) { }
        @Override
        protected void doRollback(DefaultTransactionStatus status) { }
    }
}
