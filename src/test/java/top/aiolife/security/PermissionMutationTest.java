package top.aiolife.security;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import top.aiolife.core.ApiRequestFixtures;
import top.aiolife.record.api.*;
import top.aiolife.record.mapper.*;
import top.aiolife.record.pojo.entity.*;
import top.aiolife.record.service.*;
import top.aiolife.record.service.impl.TimeTrackerCategoryServiceImpl;
import top.aiolife.record.service.impl.UserDictDataServiceImpl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 使用两个虚构 owner，检查越权请求不会进入不受约束的写入/副作用。无 Spring 或外部服务。 */
class PermissionMutationTest {
    @BeforeAll
    static void tables() {
        for (Class<?> type : List.of(TaskEntity.class, TaskColumnEntity.class, TaskDetailEntity.class,
                ThoughtEntity.class, ThoughtRelaEventEntity.class, PerformanceEntity.class, FileEntity.class,
                HonorRecordEntity.class, TimeTrackerCategoryEntity.class, UserDictDataEntity.class)) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), type.getName()), type);
        }
    }

    @Test
    void privateCategory_cannotBecomePublicOrAnotherUsersRecord() {
        for (long injectedOwner : List.of(0L, 22L)) {
            var service = spy(new TimeTrackerCategoryServiceImpl());
            var existing = new TimeTrackerCategoryEntity();
            existing.setId(7L); existing.setUserId(11L);
            var updates = new TimeTrackerCategoryEntity();
            updates.setUserId(injectedOwner); updates.setTemplateId(99L); updates.setName("本人分类");
            doReturn(existing).when(service).getById(7L);
            doReturn(true).when(service).update(any(TimeTrackerCategoryEntity.class), any(Wrapper.class));
            service.updateCategory(7L, updates, 11L);
            assertEquals(11L, updates.getUserId());
            assertNull(updates.getTemplateId());
            var query = ArgumentCaptor.forClass(Wrapper.class);
            verify(service).update(eq(updates), query.capture());
            assertTrue(query.getValue().getSqlSegment().contains("user_id"));
            verify(service, never()).updateById(any(TimeTrackerCategoryEntity.class));
        }
    }

    @Test
    void privateDictionary_cannotChangeOwnerOrTemplate() {
        var service = spy(new UserDictDataServiceImpl());
        var existing = new UserDictDataEntity();
        existing.setId(7L); existing.setUserId(11L);
        var updates = new UserDictDataEntity();
        updates.setUserId(0L); updates.setTemplateId(99L);
        doReturn(existing).when(service).getById(7L);
        doReturn(true).when(service).update(any(UserDictDataEntity.class), any(Wrapper.class));
        service.updateDictData(7L, updates, 11L);
        assertEquals(11L, updates.getUserId());
        assertNull(updates.getTemplateId());
        verify(service, never()).updateById(any(UserDictDataEntity.class));
    }

    @Test
    void foreignParentCategory_isRejectedBeforeWriting() {
        var service = spy(new TimeTrackerCategoryServiceImpl());
        var foreign = new TimeTrackerCategoryEntity(); foreign.setUserId(22L);
        doReturn(foreign).when(service).getById(7L);
        assertThrows(RuntimeException.class, () -> service.updateCategory(7L, new TimeTrackerCategoryEntity(), 11L));
        verify(service, never()).update(any(TimeTrackerCategoryEntity.class), any(Wrapper.class));
    }

    @Test
    void sorting_doesNotWriteInjectedOwnerOrContent() {
        try (var login = mockStatic(StpUtil.class)) {
            login.when(StpUtil::getLoginIdAsLong).thenReturn(11L);
            var mapper = mock(ITaskMapper.class);
            var controller = new TaskController(mock(ITaskService.class), mapper, mock(ITaskDetail.class));
            var input = new TaskEntity(); input.setId(7L); input.setUserId(22L);
            input.setContent("不要写入"); input.setCreateUser(22L); input.setSortOrder(3);
            controller.reSort(ApiRequestFixtures.requests(List.of(input), top.aiolife.record.pojo.req.TaskSortReq.class));
            var saved = ArgumentCaptor.forClass(TaskEntity.class);
            var query = ArgumentCaptor.forClass(Wrapper.class);
            verify(mapper).update(saved.capture(), query.capture());
            assertNull(saved.getValue().getUserId()); assertNull(saved.getValue().getCreateUser());
            assertNull(saved.getValue().getContent()); assertEquals(3, saved.getValue().getSortOrder());
            assertTrue(query.getValue().getSqlSegment().contains("user_id"));
        }
    }

    @Test
    void columnAndDetailSorting_onlyWriteSortFields() {
        try (var login = mockStatic(StpUtil.class)) {
            login.when(StpUtil::getLoginIdAsLong).thenReturn(11L);
            var columns = mock(ITaskColumnMapper.class);
            var column = new TaskColumnEntity(); column.setId(7L); column.setUserId(22L); column.setSortOrder(4);
            new TaskColumnController(columns, mock(ITaskColumnService.class)).reSort(ApiRequestFixtures.requests(List.of(column), top.aiolife.record.pojo.req.TaskColumnSortReq.class));
            var savedColumn = ArgumentCaptor.forClass(TaskColumnEntity.class);
            verify(columns).update(savedColumn.capture(), any(Wrapper.class));
            assertNull(savedColumn.getValue().getUserId());
            assertEquals(4, savedColumn.getValue().getSortOrder());

            var details = mock(ITaskDetail.class);
            var detail = new TaskDetailEntity(); detail.setId(8L); detail.setUserId(22L);
            detail.setTaskId(99L); detail.setContent("不要写入"); detail.setSort(5);
            new TaskDetailController(details, mock(ITaskService.class)).reSort(ApiRequestFixtures.requests(List.of(detail), top.aiolife.record.pojo.req.TaskDetailSortReq.class));
            var savedDetail = ArgumentCaptor.forClass(TaskDetailEntity.class);
            verify(details).update(savedDetail.capture(), any(Wrapper.class));
            assertNull(savedDetail.getValue().getUserId()); assertNull(savedDetail.getValue().getTaskId());
            assertNull(savedDetail.getValue().getContent()); assertEquals(5, savedDetail.getValue().getSort());
        }
    }

    @Test
    void detailUpdate_keepsAuthenticatedOwner() {
        try (var login = mockStatic(StpUtil.class)) {
            login.when(StpUtil::getLoginIdAsLong).thenReturn(11L);
            var service = mock(ITaskDetail.class);
            var controller = new TaskDetailController(service, mock(ITaskService.class));
            var input = new TaskDetailEntity(); input.setId(7L); input.setUserId(22L);
            controller.update(ApiRequestFixtures.request(input, top.aiolife.record.pojo.req.TaskDetailUpdateReq.class));
            var saved = ArgumentCaptor.forClass(TaskDetailEntity.class);
            var query = ArgumentCaptor.forClass(Wrapper.class);
            verify(service).update(saved.capture(), query.capture());
            assertEquals(11L, saved.getValue().getUserId());
            assertEquals(7L, saved.getValue().getId());
            assertTrue(query.getValue().getSqlSegment().contains("user_id"));
        }
    }

    @Test
    void foreignThoughtEvent_isNeverUpsertedOrReparented() {
        try (var login = mockStatic(StpUtil.class)) {
            login.when(StpUtil::getLoginIdAsLong).thenReturn(11L);
            var thoughts = mock(IThoughtMapper.class); var events = mock(IRelaEventMapper.class);
            when(thoughts.update(any(ThoughtEntity.class), any(Wrapper.class))).thenReturn(1);
            when(events.update(any(ThoughtRelaEventEntity.class), any(Wrapper.class))).thenReturn(0);
            var controller = new ThoughtController(thoughts, events, null);
            var input = new ThoughtEntity(); var event = new ThoughtRelaEventEntity();
            event.setId(99L); event.setThoughtId(88L); event.setContent("外来事件"); input.setEvents(List.of(event));
            assertThrows(IllegalArgumentException.class, () -> controller.update(7L,ApiRequestFixtures.request(input, top.aiolife.record.pojo.req.ThoughtUpdateReq.class)));
            var saved = ArgumentCaptor.forClass(ThoughtRelaEventEntity.class);
            var query = ArgumentCaptor.forClass(Wrapper.class);
            verify(events).update(saved.capture(), query.capture());
            assertNull(saved.getValue().getThoughtId());
            assertTrue(query.getValue().getSqlSegment().contains("thought_id"));
            verify(events, never()).insertOrUpdate(any(ThoughtRelaEventEntity.class));
            verify(events, never()).insert(any(ThoughtRelaEventEntity.class));
        }
    }

    @Test
    void missingBusinessUpdate_neverBindsAttachments() {
        try (var login = mockStatic(StpUtil.class)) {
            login.when(StpUtil::getLoginIdAsLong).thenReturn(11L);
            var files = mock(IFileService.class);
            var performance = new PerformanceEntity(); performance.setId(99L);
            performance.setCreateUser(22L); performance.setFileIds(List.of("dummy-file"));
            var performanceMapper = mock(IPerformanceMapper.class);
            assertThrows(IllegalArgumentException.class, () ->
                    new PerformanceController(performanceMapper, files).updatePerformance(ApiRequestFixtures.request(performance, top.aiolife.record.pojo.req.PerformanceUpdateReq.class)));
            var performanceUpdate = ArgumentCaptor.forClass(PerformanceEntity.class);
            verify(performanceMapper).update(performanceUpdate.capture(), any(Wrapper.class));
            assertEquals(11L, performanceUpdate.getValue().getCreateUser());
            var honor = new HonorRecordEntity(); honor.setId(99L); honor.setFileIds(List.of("dummy-file"));
            assertThrows(IllegalArgumentException.class, () ->
                    new HonorRecordController(mock(IHonorRecordMapper.class), mock(IHonorRecordService.class), files)
                            .updateHonorRecord(ApiRequestFixtures.request(honor, top.aiolife.record.pojo.req.HonorRecordUpdateReq.class)));
            verifyNoInteractions(files);
        }
    }

    @Test
    void successfulBusinessUpdate_stillBindsOwnAttachments() {
        try (var login = mockStatic(StpUtil.class)) {
            login.when(StpUtil::getLoginIdAsLong).thenReturn(11L);
            var files = mock(IFileService.class); var mapper = mock(IPerformanceMapper.class);
            when(mapper.update(any(PerformanceEntity.class), any(Wrapper.class))).thenReturn(1);
            // 附件同步会先查询当前用户拥有的文件，再执行绑定。
            var ownedFile = new FileEntity(); ownedFile.setId("31"); ownedFile.setCreateUser(11L);
            when(files.list(any(Wrapper.class))).thenReturn(List.of(ownedFile));
            var input = new PerformanceEntity(); input.setId(7L); input.setFileIds(List.of("31"));
            new PerformanceController(mapper, files).updatePerformance(ApiRequestFixtures.request(input, top.aiolife.record.pojo.req.PerformanceUpdateReq.class));
            var query = ArgumentCaptor.forClass(Wrapper.class);
            verify(files).list(query.capture());
            assertTrue(query.getValue().getSqlSegment().contains("id IN"));
            assertTrue(query.getValue().getSqlSegment().contains("create_user ="));
            assertTrue(((AbstractWrapper<?, ?, ?>) query.getValue()).getParamNameValuePairs()
                    .values().containsAll(List.of("31", 11L)));
            verify(files).bindBizId(List.of("31"), "performance", 7L);
        }
    }

    @Test
    void globalNotification_requiresAdmin() throws Exception {
        var role = LeetcodeController.class.getMethod("notifyTodayQuestion").getAnnotation(SaCheckRole.class);
        assertNotNull(role); assertArrayEquals(new String[]{"admin"}, role.value());
        assertNotNull(ThoughtController.class.getMethod("update", Long.class, top.aiolife.record.pojo.req.ThoughtUpdateReq.class)
                .getAnnotation(org.springframework.transaction.annotation.Transactional.class));
    }
}
